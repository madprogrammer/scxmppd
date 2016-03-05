package com.scxmpp.routing

import akka.util.Timeout
import com.scxmpp.akka.{CustomDistributedPubSubExtension, CustomDistributedPubSubMediator}
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.JID
import com.scxmpp.server.ServerContext

import com.typesafe.config.Config

import scala.collection.mutable
import akka.event.LoggingReceive
import akka.actor._
import akka.pattern.ask
import java.util.logging.Logger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class Route(from: JID, to: JID, element: XmlElement)
case class Subscribe(module: ActorRef)
case object SubscribeAck
case object NotInterested

class Router(serverContext: ServerContext, config: Config) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.SendToAll
  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val logger = Logger.getLogger(getClass.getName)
  val modules = new mutable.ArrayBuffer[ActorRef]

  def receive = LoggingReceive {
    case Subscribe(module) =>
      modules.append(module)
      sender ! SubscribeAck
    case route@Route(from, to, element) =>
      implicit val timeout = new Timeout(60.seconds)
      implicit val ec = context.dispatcher
      var result: Option[Route] = Some(route)
      val futures = modules.map(module => {
        module ? route
      })
      Future.sequence(futures) onComplete {
        case Failure(cause) =>
          logger.warning("An error as occured while processing by modules: " + cause)
        case Success(results) =>
          if (results.contains(None))
            result = None

          result match {
            case Some(msg) =>
              mediator ! SendToAll("/user/c2s/%s".format(to.toActorPath), msg, allButSelf = false)
            case None =>
              logger.warning("Not routing message after processing by modules " + route)
          }
      }
    }
}
