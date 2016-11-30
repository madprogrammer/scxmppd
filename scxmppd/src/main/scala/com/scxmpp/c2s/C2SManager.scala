package com.scxmpp.c2s

import com.scxmpp.akka.{CustomDistributedPubSubExtension, CustomDistributedPubSubMediator}
import com.scxmpp.server.ServerContext
import io.netty.channel.ChannelHandlerContext
import akka.event.LoggingReceive
import akka.actor._

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
          // TODO: remove old actor
          mediator ! Put(actorRef)
        case None =>
          throw new IllegalArgumentException("JID was not initialized")
      }
  }
}
