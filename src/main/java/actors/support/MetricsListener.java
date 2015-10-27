package actors.support;

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.cluster.metrics.ClusterMetricsChanged;
import akka.cluster.metrics.ClusterMetricsExtension;
import akka.cluster.metrics.NodeMetrics;
import akka.cluster.metrics.StandardMetrics;
import akka.cluster.metrics.StandardMetrics.Cpu;
import akka.cluster.metrics.StandardMetrics.HeapMemory;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MetricsListener extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	Cluster cluster = Cluster.get(getContext().system());
	ClusterMetricsExtension extension = ClusterMetricsExtension.get(getContext().system());

	// Subscribe unto ClusterMetricsEvent events.
	@Override
	public void preStart() {
		// extension.subscribe(getSelf());
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), MemberEvent.class, UnreachableMember.class);
	}

	// Unsubscribe from ClusterMetricsEvent events.
	@Override
	public void postStop() {
		//extension.unsubscribe(getSelf());
		cluster.unsubscribe(getSelf());
	}

	@Override
	public void onReceive(Object message) {
		if (message instanceof ClusterMetricsChanged) {
			ClusterMetricsChanged clusterMetrics = (ClusterMetricsChanged) message;
			for (NodeMetrics nodeMetrics : clusterMetrics.getNodeMetrics()) {
				if (nodeMetrics.address().equals(cluster.selfAddress())) {
					logHeap(nodeMetrics);
					logCpu(nodeMetrics);
				}
			}
		}
		if (message instanceof CurrentClusterState) {
			// Ignore.
		}
		if (message instanceof MemberUp) {
			MemberUp mUp = (MemberUp) message;
			log.info("Member is Up: {}", mUp.member());

		} else if (message instanceof UnreachableMember) {
			UnreachableMember mUnreachable = (UnreachableMember) message;
			log.info("Member detected as unreachable: {}", mUnreachable.member());

		} else if (message instanceof MemberRemoved) {
			MemberRemoved mRemoved = (MemberRemoved) message;
			log.info("Member is Removed: {}", mRemoved.member());

		} else if (message instanceof MemberEvent) {
			// ignore

		} else {
			unhandled(message);
		}
	}

	void logHeap(NodeMetrics nodeMetrics) {
		HeapMemory heap = StandardMetrics.extractHeapMemory(nodeMetrics);
		if (heap != null) {
			log.info("Used heap: {} MB", ((double) heap.used()) / 1024 / 1024);
		}
	}

	void logCpu(NodeMetrics nodeMetrics) {
		Cpu cpu = StandardMetrics.extractCpu(nodeMetrics);
		if (cpu != null && cpu.systemLoadAverage().isDefined()) {
			log.info("Load: {} ({} processors)", cpu.systemLoadAverage().get(), cpu.processors());
		}
	}
}
