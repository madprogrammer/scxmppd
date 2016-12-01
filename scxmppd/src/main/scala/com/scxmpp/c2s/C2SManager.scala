package com.scxmpp.c2s

import com.scxmpp.akka.{CustomDistributedPubSubExtension, CustomDistributedPubSubMediator}
import akka.event.LoggingReceive
import akka.actor._
import com.scxmpp.akka.CustomDistributedPubSubMediator.{Publish, Remove}
import com.scxmpp.hooks.{Hooks, Topics}
import com.typesafe.config.Config

case class CreateClientFSM(name: String, state: ClientFSM.State, data: ClientFSM.ClientState)
case class ReplaceClientFSM(state: ClientFSM.State, data: ClientFSM.ClientState)

class C2SManager(config: Config) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.Put
  val mediator = CustomDistributedPubSubExtension(context.system).mediator

  def receive = LoggingReceive {
    case CreateClientFSM(name, state, data) =>
      val actorRef = context.actorOf(Props(classOf[ClientFSM], config, state, data).withDeploy(Deploy.local), name)
      mediator ! Put(actorRef)
      sender ! actorRef
    case ReplaceClientFSM(state, data) =>
      data.jid match {
        case Some(jid) =>
          val actorRef = context.actorOf(Props(classOf[ClientFSM], config, state, data).withDeploy(Deploy.local),
            jid.toActorPath)
          actorRef ! ClientFSM.Initialize
          sender ! ClientFSM.Replaced(actorRef)

          mediator ! Remove(sender.path.toString)
          mediator ! Put(actorRef)
          mediator ! Publish(Topics.ClientFSMReplaced, Hooks.ClientFSMReplaced(sender.path.name, jid.toActorPath),
            sendOneMessageToEachGroup = false, onlyLocal = true)
        case None =>
          throw new IllegalArgumentException("JID was not initialized")
      }
  }
}
