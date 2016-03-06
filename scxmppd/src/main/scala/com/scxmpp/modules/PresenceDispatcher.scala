package com.scxmpp.modules

import akka.event.LoggingReceive
import com.scxmpp.akka.CustomDistributedPubSubMediator.Publish
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.modules.support.ModuleActor

import com.scxmpp.routing.{NotInterested, Route, Subscribe, SubscribeAck}
import com.scxmpp.server.ServerContext
import com.scxmpp.xml.XmlElement
import com.typesafe.config.Config

/**
  * This module publishes presence updates to the appropriate topic
  * via the CustomDistributedPubSubMediator
  */
class PresenceDispatcher(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  router ! Subscribe(self)

  def receive = {
    case SubscribeAck =>
      context become ready
  }

  def ready = LoggingReceive {
    case Route(from, to, msg@XmlElement("presence", _, _, _)) =>
      if (config.getStringList("xmpp.hosts") contains from.server) {
        (msg.child("priority"), msg("type")) match {
          case (Some(priority), typ) =>
            val value = try {
              priority.body.toInt
            } catch {
              case e: NumberFormatException => 0
            }
            mediator ! Publish (Topics.PresenceUpdate, Hooks.PresenceUpdate (from, value, typ),
                sendOneMessageToEachGroup = false, onlyLocal = true)
          case (None, typ) =>
            mediator ! Publish (Topics.PresenceUpdate, Hooks.PresenceUpdate (from, 0, typ),
              sendOneMessageToEachGroup = false, onlyLocal = true)
        }
      }
      sender ! NotInterested
    case _ =>
      sender ! NotInterested
  }

}