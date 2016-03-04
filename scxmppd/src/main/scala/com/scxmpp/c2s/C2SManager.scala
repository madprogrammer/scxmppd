package com.scxmpp.c2s

import com.scxmpp.akka.{CustomDistributedPubSubExtension, CustomDistributedPubSubMediator}
import com.scxmpp.server.ServerContext
import io.netty.channel.ChannelHandlerContext
import akka.event.LoggingReceive
import akka.actor._

import com.typesafe.config.Config

case class CreateClientFSM(ctx: ChannelHandlerContext, name: String, state: ClientFSM.State, data: ClientFSM.Data)

class C2SManager(config: Config) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.Put
  val mediator = CustomDistributedPubSubExtension(context.system).mediator

  def receive = LoggingReceive {
    case CreateClientFSM(ctx, name, state, data) =>
      val actorRef = context.actorOf(Props(classOf[ClientFSM], config, ctx, state, data).withDeploy(Deploy.local), name)
      mediator ! Put(actorRef)
      sender ! actorRef
  }
}
