package main.scala

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.LoggingReceive
import akka.actor._
import akka.util.Timeout
import akka.contrib.pattern.{DistributedPubSubMediator, DistributedPubSubExtension}

case class Route(from: JID, to: JID, element: XmlElement)

class Router extends Actor with ActorLogging {
  import DistributedPubSubMediator.Send
  val mediator = DistributedPubSubExtension(context.system).mediator

  def receive = LoggingReceive {
    case Route(from, to, element) =>
      mediator ! Send("/user/c2s/%s".format(to.toActorPath), ClientFSM.Incoming(from, to, element), false)
    }
}
