package com.scxmpp.modules

import com.scxmpp.modules.support.ModuleActor
import com.scxmpp.routing.Route
import com.scxmpp.server.ServerContext
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.IQ

import com.typesafe.config.Config

object LastActivityModuleDefinitions
{
  val NS_LAST: String = "jabber:iq:last"
}

class LastActivityModule(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  registerHandler(s"iq.${LastActivityModuleDefinitions.NS_LAST}", classOf[LastActivityIqHandler])

  def receive = {
    case _ =>
  }
}

class LastActivityIqHandler(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  def receive = {
    case Route(from, to, msg @ XmlElement("iq", _, _, _)) =>
      (msg("id"), msg("type")) match {
        case (Some(id), Some("get"))  =>
          msg.child("query") match {
            case Some(query) =>
              if (query("xmlns").contains(LastActivityModuleDefinitions.NS_LAST))
                sender ! Route(to, from, IQ(id, "result"))
            case _ =>
          }
        case _ =>
      }
    case _ =>
  }

}