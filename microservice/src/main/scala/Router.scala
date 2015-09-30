package main.scala

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.LoggingReceive
import akka.actor._
import akka.util.Timeout

case class Route(from: JID, to: JID, element: XmlElement)

class Router extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.SendToAll
  val mediator = CustomDistributedPubSubExtension(context.system).mediator

  def receive = LoggingReceive {
    case Route(from, to, element) =>
      mediator ! SendToAll("/user/c2s/%s".format(to.toActorPath), ClientFSM.Incoming(from, to, element), false)
    }
}
