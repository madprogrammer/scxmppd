package com.scxmpp.bosh

import akka.actor.ActorSystem
import com.scxmpp.xml.XmlElement
import io.netty.channel.{SimpleChannelInboundHandler, ChannelHandlerContext}

class BoshXmppServerHandler(actorSystem: ActorSystem) extends SimpleChannelInboundHandler[XmlElement] {
  val NS_BOSH = "http://jabber.org/protocol/httpbind"

  override def channelActive(ctx: ChannelHandlerContext) {
    super.channelActive(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext) {
    super.channelInactive(ctx)
  }
  override def channelRead0(ctx: ChannelHandlerContext, msg: XmlElement) = {
    msg match {
      case XmlElement("body", _, "", List()) =>
        msg("xmlns") match {
          case Some(NS_BOSH) =>
            msg("sid") match {
              case Some(sid) =>
                // Existing session
              case _ =>
                // Request to create new session
                println("Writing response")
                ctx.writeAndFlush(XmlElement("body", List(("xmlns", NS_BOSH)), "", List()))
            }
          case _ =>
        }
    }
  }
}
