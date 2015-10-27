package worker;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class EchoActor extends UntypedActor {

	  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	@Override
	public void onReceive(Object message) throws Exception {
		log.info("Received Message {} in Actor {}", message.toString(), getSelf().path().address());
	}

}
