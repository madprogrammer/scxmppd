package main.scala

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.LoggingReceive
import akka.actor._
import akka.util.Timeout

case class Route(from: JID, to: JID, element: XmlElement)

class Router extends Actor with ActorLogging {
  def receive = LoggingReceive {
    case Route(from, to, element) =>
      implicit val timeout = Timeout(5 seconds)
      context.actorSelection("/user/c2s/%s".format(to.toActorPath)).resolveOne onComplete {
        case Success(dest) =>
          dest ! ClientFSM.Incoming(from, to, element)
        case Failure(ex) =>
          // TODO: handle non-resolved actor
      }
    }
}
