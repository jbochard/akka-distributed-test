package worker;

import akka.actor.UntypedActor;

public class Frontend extends UntypedActor {


//  ActorRef masterProxy = getContext().actorOf(
//      ClusterSingletonProxy.props(
//          "/user/master",
//          ClusterSingletonProxySettings.create(getContext().system()).withRole("backend")),
//      "masterProxy");
//
  public void onReceive(Object message) {

  }
}
