package com.scxmpp.modules

import akka.event.LoggingReceive
import com.scxmpp.util.Helpers
import com.scxmpp.xmpp.StanzaError

import scala.util.{Failure, Success}

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.pattern.ask
import akka.util.Timeout

import com.scxmpp.akka.CustomDistributedPubSubMediator
import com.scxmpp.hooks.{Topics, Hooks}
import com.scxmpp.routing.Route
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

  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}

  mediator ! Subscribe(Topics.MessageRouted, self)

  def receive = {
    case SubscribeAck(Subscribe(Topics.MessageRouted, None, `self`)) =>
      context become ready
  }

  def ready = LoggingReceive {
    case Hooks.MessageRouted(Route(from, to, msg@XmlElement("iq", _, _, _))) =>
      if (config.getStringList("xmpp.hosts") contains to.toString) {
        (msg("id"), msg("type")) match {
          case (Some(_), Some("get")) =>
            msg.firstChild match {
              case Some(child) =>
                implicit val timeout = Timeout(5 seconds)
                implicit val ec = context.dispatcher
                child("xmlns") match {
                  case Some(xmlns) =>
                    val encodedNamespace = Helpers.urlEncode(xmlns)
                    val actorSel = context.actorSelection(s"/user/handler/iq.$encodedNamespace")
                    actorSel ? Route(from, to, msg) andThen {
                      case Success(result: Route) =>
                        router ! result
                      case Failure(failure) =>
                        // TODO: logging
                        println("Got failure " + failure)
                        router ! Route(to, from, StanzaError(msg, StanzaError.ServiceUnavailable))
                      case other =>
                        println("Got other " + other)
                    }
                  case None =>
                    router ! Route(to, from, StanzaError(msg, StanzaError.BadRequest))
                }
              case _ =>
                router ! Route(to, from, StanzaError(msg, StanzaError.BadRequest))
            }
          case _ =>
        }
      }
  }

}