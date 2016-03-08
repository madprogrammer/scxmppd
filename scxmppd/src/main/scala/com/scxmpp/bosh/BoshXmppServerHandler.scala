package com.scxmpp.bosh

import akka.util.Timeout
import com.scxmpp.c2s.ClientFSM
import com.scxmpp.server.ServerContext
import com.scxmpp.util.RandomUtils
import com.scxmpp.xml.XmlElement
import io.netty.channel.{ChannelPromise, ChannelDuplexHandler, ChannelHandlerContext}

import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Failure, Success}

object TerminalCondition {
  val ERR_BAD_REQUEST = "bad-request"
  val ERR_HOST_UNKNOWN = "host-unknown"
  val ERR_IMPROPER_ADDRESSING = "improper-addressing"
  val ERR_INTERNAL_ERROR = "internal-server-error"
  val ERR_ITEM_NOT_FOUND = "item-not-found"
  val ERR_POLICY_VIOLATION = "policy-violation"
  val ERR_SYSTEM_SHUTDOWN = "system-shutdown"
}

object BoshDefaults {
  val DEFAULT_TIMEOUT = 60
  val DEFAULT_INACTIVITY = 30
}

class BoshXmppServerHandler(context: ServerContext) extends ChannelDuplexHandler {
  val NS_BOSH = "http://jabber.org/protocol/httpbind"
  val manager = context.actorSystem.actorSelection("/user/bosh")

  override def channelActive(ctx: ChannelHandlerContext) {
    super.channelActive(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext) {
    super.channelInactive(ctx)
  }

  private def terminateWithCondition(ctx: ChannelHandlerContext, condition: String) = {
    ctx.channel.writeAndFlush(XmlElement("body",
      List(("type", "terminate"), ("condition", condition), ("xmlns", NS_BOSH)), "", List()))
  }

  override def write(ctx: ChannelHandlerContext, obj: Object, promise: ChannelPromise): Unit = {
    val msg = obj.asInstanceOf[XmlElement]
    msg match {
      case XmlElement("body", _, _, _) =>
        super.write(ctx, msg, promise)
      case other @ XmlElement(_, _, _, _) =>
        super.write(ctx, XmlElement("body", List(("xmlns", NS_BOSH)), "", List(msg)), promise)
    }
  }

  override def channelRead(ctx: ChannelHandlerContext, obj: Object) = {
    val msg = obj.asInstanceOf[XmlElement]
    println(msg)
    msg match {
      case XmlElement("body", _, "", List()) =>
        msg("xmlns") match {
          case Some(NS_BOSH) =>
            msg("sid") match {
              case Some(sid) =>
                // Existing session
                ctx.channel.writeAndFlush(XmlElement("body", List(
                  ("xmlns", NS_BOSH)), "", List()))
              case _ =>
                // Request to create new session
                msg("rid") match {
                  case Some(rid) =>
                    val wait = msg("wait") match {
                      case Some(some) => some.toInt
                      case _ => BoshDefaults.DEFAULT_TIMEOUT
                    }
                    val hold = msg("hold") match {
                      case Some(some) => some
                      case _ => "1"
                    }
                    val newSid = RandomUtils.randomString(16)
                    implicit val timeout = Timeout(5.seconds)

                    // Here we go creating a new session
                    manager ? CreateClientFSM(newSid, newSid, ClientFSM.WaitForStream,
                      ClientFSM.ClientState(RandomUtils.randomDigits(10), context = ctx)) onComplete {
                      case Success(_) =>
                        ctx.channel.writeAndFlush(XmlElement("body", List(
                          ("xmlns", NS_BOSH),
                          ("wait", wait.toString),
                          ("hold", hold),
                          ("ver", "1.0"),
                          ("accept", "deflate,gzip"),
                          ("ack", rid),
                          ("maxpause", "%d".format(wait * 2)),
                          ("requests", "2"),
                          ("sid", newSid),
                          ("xmpp:restartlogic", "true"),
                          ("xmpp:version", "1.0"),
                          ("xmlns:xmpp", "urn:xmpp:xbosh"),
                          ("xmlns:stream", "http://etherx.jabber.org/streams")),
                          "", List()))
                      case Failure(_) =>
                        terminateWithCondition(ctx, TerminalCondition.ERR_INTERNAL_ERROR)
                    }
                  case None =>
                    terminateWithCondition(ctx, TerminalCondition.ERR_BAD_REQUEST)
                }
            }
          case _ =>
            terminateWithCondition(ctx, TerminalCondition.ERR_BAD_REQUEST)
        }
    }
  }
}
