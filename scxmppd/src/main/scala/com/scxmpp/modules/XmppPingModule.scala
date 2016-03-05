package com.scxmpp.modules

import akka.event.LoggingReceive
import com.scxmpp.akka.CustomDistributedPubSubMediator.{Subscribe, SubscribeAck, Publish}
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.modules.support.ModuleActor
import com.scxmpp.routing.Route
import com.scxmpp.server.ServerContext
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.IQ

import com.typesafe.config.Config

object XmppPingModuleDefinitions
{
  val NS_PING: String = "urn:xmpp:ping"
}

class XmppPingModule(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  registerHandler(s"iq.${XmppPingModuleDefinitions.NS_PING}", classOf[XmppPingIqHandler])

  def receive = {
    case _ =>
  }
}

class XmppPingIqHandler(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  mediator ! Subscribe(Topics.ModulesLoaded, self)

  def receive = {
    case SubscribeAck(Subscribe(Topics.ModulesLoaded, None, `self`)) =>
      mediator ! Publish(Topics.DiscoveryFeature, Hooks.DiscoveryFeature(XmppPingModuleDefinitions.NS_PING),
        sendOneMessageToEachGroup = false, onlyLocal = true)
    case SubscribeAck(Subscribe(Topics.SessionClosed, None, `self`)) =>
      context become ready
  }

  def ready = LoggingReceive {
    case Route(from, to, msg @ XmlElement("iq", _, _, _)) =>
      (msg("id"), msg("type")) match {
        case (Some(id), Some("get"))  =>
          msg.child("ping") match {
            case Some(ping) =>
              if (ping("xmlns").contains(XmppPingModuleDefinitions.NS_PING))
                sender ! Route(to, from, IQ(id, "result"))
            case _ =>
          }
        case _ =>
      }
    case _ =>
  }

}