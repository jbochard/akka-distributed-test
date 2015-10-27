package actors.support;

import akka.actor.DeadLetter;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class DeadLetterActor extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	@Override
	public void preStart() throws Exception {
		
		context().system().eventStream().subscribe(getSelf(), DeadLetter.class);
	}
	@Override
	public void onReceive(Object arg0) throws Exception {
		//log.info(arg0.toString());
	}
}
