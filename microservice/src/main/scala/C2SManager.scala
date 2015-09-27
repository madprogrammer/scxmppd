package main.scala

import io.netty.channel.ChannelHandlerContext
import akka.event.LoggingReceive
import akka.actor._

case class CreateClientFSM(ctx: ChannelHandlerContext, name: String, state: ClientFSM.State, data: ClientFSM.Data)

class C2SManager(serverContext: MicroserviceContext, actorSystem: ActorSystem) extends Actor with ActorLogging {
  def receive = LoggingReceive {
    case CreateClientFSM(ctx, name, state, data) =>
      sender ! context.actorOf(Props(classOf[ClientFSM], serverContext, ctx, state, data).withDeploy(Deploy.local), name)
  }
}
