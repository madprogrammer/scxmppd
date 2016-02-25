package com.scxmpp.hooks

import akka.actor.ActorRef

object Hooks {
  case class SessionOpened(jid: JID, actor: ActorRef)
  case class SessionClosed(jid: JID, actor: ActorRef)
  case class MessageRouted(message: Route)
}

object Topics {
  val SessionOpened = "session-opened"
  val SessionClosed = "session-closed"
  val MessageRouted = "message-routed"
}
