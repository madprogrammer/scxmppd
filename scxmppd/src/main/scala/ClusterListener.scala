package main.scala

import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import akka.actor._
import akka.cluster._

class ClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart() {
    cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop() {
    cluster.unsubscribe(self)
  }

  def receive = LoggingReceive {
    case MemberUp(member) =>
      log.info(s"[ClusterListener] node is up: $member")

    case UnreachableMember(member) =>
      log.info(s"[ClusterListener] node is unreachable: $member")

    case MemberRemoved(member, prevStatus) =>
      log.info(s"[ClusterListener] node is removed: $member after $prevStatus")

    case ev: MemberEvent =>
      log.info(s"[ClusterListener] event: $ev")
  }
}
