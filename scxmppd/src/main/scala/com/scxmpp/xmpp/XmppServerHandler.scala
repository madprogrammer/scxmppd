package com.scxmpp.xmpp

import java.net.InetSocketAddress
import java.util.logging.Logger

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.scxmpp.c2s.{ClientFSM, CreateClientFSM}
import com.scxmpp.util.RandomUtils
import com.scxmpp.xml.XmlElement
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.DecoderException

import scala.concurrent.Await
import scala.concurrent.duration._

class XmppServerHandler(actorSystem: ActorSystem) extends SimpleChannelInboundHandler[XmlElement] {

  val manager = actorSystem.actorSelection("/user/c2s")
  val logger = Logger.getLogger(getClass.getName)
  var fsm: ActorRef = _

  def createFSM(ctx: ChannelHandlerContext): ActorRef = {
    implicit val timeout = Timeout(60.seconds)
    val (ip, port) = ctx.channel.remoteAddress match { case s: InetSocketAddress => (s.getAddress.getHostAddress, s.getPort) }
    val name = ip + ":" + port + "@" + RandomUtils.randomDigits(5)
    val future = manager ? CreateClientFSM(ctx, name,
      ClientFSM.WaitForStream,
      ClientFSM.ClientState(RandomUtils.randomDigits(10)))
    Await.result(future, timeout.duration).asInstanceOf[ActorRef]
  }

  // Called from ClientFSM to replace it while keeping state
  def replaceFSM(ctx: ChannelHandlerContext, state: ClientFSM.State, data: ClientFSM.ClientState) {
    implicit val timeout = Timeout(60.seconds)
    data.jid match {
      case None =>
        throw new IllegalArgumentException("JID was not initialized")
      case Some(jid) =>
        val previous = fsm
        val future = manager ? CreateClientFSM(ctx, jid.toActorPath, state, data)
        fsm = Await.result(future, timeout.duration).asInstanceOf[ActorRef]
        fsm ! ClientFSM.Initialize
        previous ! ClientFSM.Replaced(fsm)
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: XmlElement) =
    fsm ! msg

  override def channelActive(ctx: ChannelHandlerContext) {
    super.channelActive(ctx)
    fsm = createFSM(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext) {
    fsm ! ClientFSM.Disconnected
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    logger.warning("Unexpected exception: " + cause)
    cause.printStackTrace()
    cause match {
      case e: DecoderException =>
        fsm ! ClientFSM.ParseError
      case e =>
        fsm ! ClientFSM.ExceptionCaught(e)
    }
  }

}
