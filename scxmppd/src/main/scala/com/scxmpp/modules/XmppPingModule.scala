package com.scxmpp.modules

import akka.actor._
import com.scxmpp.akka.CustomDistributedPubSubMediator
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.routing.Route
import com.scxmpp.server.ServerContext
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.IQ

class XmppPingModule(serverContext: ServerContext) extends ModuleActor(serverContext) {
  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}

  mediator ! Subscribe(Topics.MessageRouted, self)

  def receive = {
    case SubscribeAck(Subscribe(Topics.MessageRouted, None, `self`)) =>
      context become ready
  }

  def ready: Receive = {
    case Hooks.MessageRouted(Route(from, to, msg @ XmlElement("iq", _, _, _))) =>
      if (serverContext.xmpp.hosts contains to.toString) {
        (msg("id"), msg("type")) match {
          case (Some(id), Some("get"))  =>
            msg.child("ping") match {
              case Some(ping) =>
                if (ping("xmlns").contains("urn:xmpp:ping"))
                  router ! Route(to, from, IQ(id, "result"))
              case _ =>
            }
          case _ =>
        }
      }
    case _ =>
  }

}
