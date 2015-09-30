package main.scala

import io.netty.channel.ChannelHandlerContext
import akka.event.LoggingReceive
import akka.actor._

case class CreateClientFSM(ctx: ChannelHandlerContext, name: String, state: ClientFSM.State, data: ClientFSM.Data)

class C2SManager(serverContext: MicroserviceContext, actorSystem: ActorSystem) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.Put
  val mediator = CustomDistributedPubSubExtension(context.system).mediator

  def receive = LoggingReceive {
    case CreateClientFSM(ctx, name, state, data) =>
      val actorRef = context.actorOf(Props(classOf[ClientFSM], serverContext, ctx, state, data).withDeploy(Deploy.local), name)
      mediator ! Put(actorRef)
      sender ! actorRef
  }
}
