package com.scxmpp.hooks

import akka.actor.ActorRef
import com.scxmpp.routing.Route
import com.scxmpp.xmpp.JID

object Hooks {
  case class SessionOpened(jid: JID, actor: ActorRef)
  case class SessionClosed(jid: JID, actor: ActorRef)
  case class DiscoveryFeature(feature: String)
}

object Topics {
  val SessionOpened = "session-opened"
  val SessionClosed = "session-closed"
  val DiscoveryFeature = "discovery-feature"
}
