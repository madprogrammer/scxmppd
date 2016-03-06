package com.scxmpp.modules

import akka.event.LoggingReceive
import akka.util.Timeout
import com.scxmpp.akka.CustomDistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.modules.support.ModuleActor

import com.scxmpp.routing.{NotInterested, Route}
import com.scxmpp.routing.{Subscribe => RouterSubscribe, SubscribeAck => RouterSubscribeAck}
import com.scxmpp.server.ServerContext
import com.scxmpp.util.Helpers
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.{JID, StanzaError}
import com.typesafe.config.Config

import scala.util.{Success, Failure}
import scala.concurrent.duration._

import scala.pickling.Defaults._
import scala.pickling.json._

object OfflineModuleDefinitions
{
  val DISCO: String = "msgoffline"
  val NS_DELAY: String = "urn:xmpp:delay"
}

/**
  * This module implements XEP-0160 Best Practices for Handling Offline Messages
  */
class OfflineModule(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  router ! RouterSubscribe(self)

  private def jidToKey(jid: JID): String = Helpers.urlEncode(jid.user) + ":" + jid.server + ":offline"

  def receive = {
    case RouterSubscribeAck =>
      mediator ! Subscribe(Topics.ModulesLoaded, self)
    case SubscribeAck(Subscribe(Topics.ModulesLoaded, None, `self`)) =>
      mediator ! Subscribe(Topics.PresenceUpdate, self)
      mediator ! Publish(Topics.DiscoveryFeature, Hooks.DiscoveryFeature(OfflineModuleDefinitions.DISCO),
        sendOneMessageToEachGroup = false, onlyLocal = true)
    case SubscribeAck(Subscribe(Topics.PresenceUpdate, None, `self`)) =>
      context become ready
  }

  def ready = LoggingReceive {
    case Route(from, to, msg@XmlElement("message", _, _, _)) =>
      implicit val ec = context.dispatcher
      implicit val timeout = Timeout(5.seconds)
      val originalSender = sender
      context.actorSelection("/user/c2s/%s".format(to.toActorPath)).resolveOne onComplete {
        case Success(_) =>
          // User seems online, just continue routing the message
          originalSender ! NotInterested
        case Failure(_) =>
          if (config.getStringList("xmpp.hosts") contains to.server) {
            msg("type") match {
              case Some("normal") | Some("chat") | None =>
                logger.info("Save message to offline queue " + msg)
                val withDelay = XmlElement(msg.name, msg.attrs, msg.body,
                  msg.children ++ List(XmlElement("delay", List(("xmlns", OfflineModuleDefinitions.NS_DELAY),
                    ("from", to.server), ("stamp", Helpers.getXmlTimestamp)), "", List())))
                serverContext.keyValueStore.addValue(jidToKey(to.withoutResource), withDelay.pickle.value)
              case _ =>
                router ! Route(to, from, StanzaError(msg, StanzaError.ServiceUnavailable))
            }
            originalSender ! None
          } else {
            originalSender ! NotInterested
          }
      }
    case Hooks.PresenceUpdate(jid, priority, typ) =>
      if (priority >= 0) {
        implicit val ec = context.dispatcher
        serverContext.keyValueStore.getValues(jidToKey(jid.withoutResource)) onComplete {
          case Failure(cause) =>
            logger.warning("Failed to retrieve offline messages " + cause)
          case Success(messages) =>
            messages.foreach((msg: String) => {
              msg.unpickle[XmlElement] match {
                case elm @ XmlElement(_, _, _, _) =>
                  println(elm)
                  router ! Route(JID(elm("from").get), JID(elm("to").get), elm)
              }
            })
        }
      }
    case _ =>
      sender ! NotInterested
  }

}