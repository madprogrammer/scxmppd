package main.scala

import akka.actor._

class XmppPingModule extends Actor {
  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}

  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  mediator ! Subscribe(Topics.MessageRouted, self)

  val router = context.actorSelection("/user/router")

  def receive = {
    case SubscribeAck(Subscribe(Topics.MessageRouted, None, `self`)) =>
      context become ready
  }

  def ready: Receive = {
    case Hooks.MessageRouted(Route(from, to, msg @ XmlElement("iq", _, _, _))) =>
      (msg("id"), msg("type")) match {
        case (Some(id), Some("get"))  =>
          msg.child("ping") match {
            case Some(ping) =>
              if (ping("xmlns") == Some("urn:xmpp:ping"))
                router ! Route(to, from, IQ(id, "result"))
            case _ =>
          }
        case _ =>
      }
    case _ =>
  }

}
