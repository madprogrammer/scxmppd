package com.scxmpp.modules

import akka.event.LoggingReceive
import com.scxmpp.modules.support.ModuleActor
import com.scxmpp.util.Helpers
import com.scxmpp.xmpp.StanzaError

import scala.util.{Failure, Success}
import scala.concurrent.duration._

import akka.pattern.ask
import akka.util.Timeout

import com.scxmpp.routing.{NotInterested, Route, Subscribe, SubscribeAck}
import com.scxmpp.server.ServerContext
import com.scxmpp.xml.XmlElement
import com.typesafe.config.Config

/**
  * This module handles all IQ stanzas and dispatches them to relevant
  * handlers (having the IqHandler trait), based on the IQ namespace.
  * In case no loaded module can handle the IQ request, an error will be
  * returned to the sender.
  */
class IqDispatcher(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  router ! Subscribe(self)

  def receive = {
    case SubscribeAck =>
      context become ready
  }

  def ready = LoggingReceive {
    case Route(from, to, msg@XmlElement("iq", _, _, _)) =>
      if (config.getStringList("xmpp.hosts") contains to.server) {
        (msg("id"), msg("type")) match {
          case (Some(_), Some("get")) =>
            msg.firstChild match {
              case Some(child) =>
                implicit val timeout = Timeout(5.seconds)
                implicit val ec = context.dispatcher
                child("xmlns") match {
                  case Some(xmlns) =>
                    val encodedNamespace = Helpers.urlEncode(xmlns)
                    val realSender = sender
                    val actorSel = context.actorSelection(s"/user/handler/iq.$encodedNamespace")
                    actorSel ? Route(from, to, msg) andThen {
                      case Success(result: Route) =>
                        router ! result
                        realSender ! None
                      case Failure(failure) =>
                        logger.warning("Got failure " + failure)
                        router ! Route(to, from, StanzaError(msg, StanzaError.ServiceUnavailable))
                        realSender ! None
                      case other =>
                        logger.warning("Got unexpected " + other)
                        realSender ! None
                    }
                  case None =>
                    router ! Route(to, from, StanzaError(msg, StanzaError.BadRequest))
                    sender ! None
                }
              case _ =>
                router ! Route(to, from, StanzaError(msg, StanzaError.BadRequest))
                sender ! None
            }
          case _ =>
            sender ! NotInterested
        }
      }
  }

}