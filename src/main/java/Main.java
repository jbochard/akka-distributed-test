
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import actors.support.DeadLetterActor;
import actors.support.MetricsListener;
import akka.actor.ActorIdentity;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.Props;
import akka.cluster.client.ClusterClient;
import akka.cluster.client.ClusterClientSettings;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.pattern.Patterns;
import akka.persistence.journal.leveldb.SharedLeveldbJournal;
import akka.persistence.journal.leveldb.SharedLeveldbStore;
import akka.routing.FromConfig;
import akka.util.Timeout;
import config.AkkaConfig;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import worker.EchoActor;
import worker.Frontend;
import worker.WorkExecutor;
import worker.WorkResultConsumer;
import worker.Worker;

public class Main {
	private static String CLUSTER_NAME = "ClusterSystem";
	private static String CLUSTER_ROLE = "backend";

	private static FiniteDuration workTimeout = Duration.create(10, "seconds");

	public static void main(String[] args) throws InterruptedException {
		int port = (args.length == 0 || !args[0].matches("[0..9]+")) ? 2551 : Integer.valueOf(args[0]);
		Map<String, Object> clusterInfo = AkkaConfig.loadClusterInfo();

		Config conf = ConfigFactory.load()
				.withValue("akka.remote.netty.tcp", AkkaConfig.getTcpSection(port, clusterInfo))
				.withValue("akka.cluster.seed-nodes", AkkaConfig.getSeedNodes(CLUSTER_NAME, port, clusterInfo));
		// .withValue("akka.cluster.roles",
		// ConfigValueFactory.fromAnyRef(Arrays.asList(CLUSTER_ROLE)));

		ActorSystem system = ActorSystem.create(CLUSTER_NAME, conf);

		// Create an actor that handles cluster domain events
		system.actorOf(Props.create(MetricsListener.class), "metricsListener");

		system.actorOf(Props.create(DeadLetterActor.class), "deadLetterActor");

		Thread.currentThread().sleep(10000);

		startEchoActor(system, clusterInfo, 20);

		// startupSharedJournal(system, (port == 2551),
		// ActorPaths.fromString("akka.tcp://ClusterSystem@10.0.2.4:2551/user/store"));
		// system.actorOf(ClusterSingletonManager.props(Master.props(workTimeout),
		// PoisonPill.getInstance(),
		// ClusterSingletonManagerSettings.create(system).withRole(role)),
		// "master");
	}

	@SuppressWarnings("unchecked")
	private static void startEchoActor(ActorSystem system, Map<String, Object> clusterInfo, int num) {
		ActorRef randomRouter = system.actorOf(Props.create(EchoActor.class).withRouter(new FromConfig()), "RandomPoolActor");

		for (int i = 0; i < num; i++) {
			randomRouter.tell(((Map<String, Object>) clusterInfo.get("instanceInfo")).get("name").toString() + " "
					+ String.valueOf(i), null);
		}
	}

	private static void startFrontEnd(ActorSystem system, int num) {
		for (int i = 0; i < num; i++) {
			system.actorOf(Props.create(Frontend.class), "frontend-" + i);
		}
	}

	public static void startWorker(int port) {
		Config conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
				.withFallback(ConfigFactory.load("worker"));

		ActorSystem system = ActorSystem.create("WorkerSystem", conf);

		ActorRef clusterClient = system.actorOf(ClusterClient.props(ClusterClientSettings.create(system)),
				"clusterClient");
		system.actorOf(Worker.props(clusterClient, Props.create(WorkExecutor.class)), "worker");
	}

	public static void startFrontend(int port) {
		Config conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
				.withFallback(ConfigFactory.load());

		ActorSystem system = ActorSystem.create("ClusterSystem", conf);
		ActorRef frontend = system.actorOf(Props.create(Frontend.class), "frontend");
		system.actorOf(Props.create(WorkResultConsumer.class), "consumer");
	}

	public static void startupSharedJournal(final ActorSystem system, boolean startStore, final ActorPath path) {
		// Start the shared journal one one node (don't crash this SPOF)
		// This will not be needed with a distributed journal
		if (startStore) {
			system.actorOf(Props.create(SharedLeveldbStore.class), "store");
		}
		// register the shared journal

		Timeout timeout = new Timeout(15, TimeUnit.SECONDS);

		ActorSelection actorSelection = system.actorSelection(path);
		Future<Object> f = Patterns.ask(actorSelection, new Identify(null), timeout);

		f.onSuccess(new OnSuccess<Object>() {

			@Override
			public void onSuccess(Object arg0) throws Throwable {
				if (arg0 instanceof ActorIdentity && ((ActorIdentity) arg0).getRef() != null) {
					SharedLeveldbJournal.setStore(((ActorIdentity) arg0).getRef(), system);
				} else {
					system.log().error("Lookup of shared journal at {} timed out", path);
					System.exit(-1);
				}

			}
		}, system.dispatcher());

		f.onFailure(new OnFailure() {
			public void onFailure(Throwable ex) throws Throwable {
				system.log().error(ex, "Lookup of shared journal at {} timed out", path);
			}
		}, system.dispatcher());
	}
}
