package com.scxmpp.hooks

import akka.actor.ActorRef
import com.scxmpp.routing.Route
import com.scxmpp.xmpp.JID

object Hooks {
  case class SessionOpened(jid: JID, actor: ActorRef)
  case class SessionClosed(jid: JID, actor: ActorRef)
  case class PresenceUpdate(jid: JID, prio: Int, typ: Option[String])
  case class DiscoveryFeature(feature: String)
  case object ModulesLoaded
}

object Topics {
  val SessionOpened = "session-opened"
  val SessionClosed = "session-closed"
  val DiscoveryFeature = "discovery-feature"
  val ModulesLoaded = "modules-loaded"
  val PresenceUpdate = "presence-update"
}
