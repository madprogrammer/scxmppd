package com.scxmpp.server

import java.util.logging.Logger
import io.netty.handler.codec.DecoderException
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import java.net.InetSocketAddress
import javax.xml.stream.events._
import scala.collection.mutable
import scala.collection.JavaConversions._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class ServerHandler(context: ServerContext, actorSystem: ActorSystem) extends SimpleChannelInboundHandler[XMLEvent] {

  val manager = actorSystem.actorSelection("/user/c2s")
  val logger = Logger.getLogger(getClass.getName)
  var nodes: mutable.Stack[XmlElement] = mutable.Stack()
  var fsm: ActorRef = _
  var depth = 0

  def getAttributeTuple(attr: Attribute) = {
    (attr.getName.getPrefix match {
      case prefix: String if prefix.length > 0 =>
        prefix + ":" + attr.getName.getLocalPart
      case prefix: String =>
        attr.getName.getLocalPart
    }, attr.getValue)
  }

  def getXmlElement(e: StartElement): XmlElement = {
    val ns = e.getName.getNamespaceURI
    XmlElement(
      e.getName.getLocalPart,
      ns match {
        case uri: String if uri.length > 0 =>
          "xmlns" -> ns :: e.getAttributes.map(x => getAttributeTuple(x.asInstanceOf[Attribute])).toList
        case uri: String =>
          e.getAttributes.map(x => getAttributeTuple(x.asInstanceOf[Attribute])).toList
      },
      "", List())
  }

  def createFSM(ctx: ChannelHandlerContext): ActorRef = {
    implicit val timeout = Timeout(60 seconds)
    val (ip, port) = ctx.channel.remoteAddress match { case s: InetSocketAddress => (s.getAddress.getHostAddress, s.getPort) }
    val name = ip + ":" + port + "@" + RandomUtils.randomDigits(5)
    val future = manager ? CreateClientFSM(ctx, name,
      ClientFSM.WaitForStream,
      ClientFSM.ClientState(RandomUtils.randomDigits(10)))
    Await.result(future, timeout.duration).asInstanceOf[ActorRef]
  }

  // Called from ClientFSM to replace it while keeping state
  def replaceFSM(ctx: ChannelHandlerContext, state: ClientFSM.State, data: ClientFSM.ClientState) {
    implicit val timeout = Timeout(60 seconds)
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

  def reset() {
    depth = 0
    nodes.clear()
  }

  override def channelActive(ctx: ChannelHandlerContext) {
    super.channelActive(ctx)
    fsm = createFSM(ctx)
  }

  override def channelInactive(ctx: ChannelHandlerContext) {
    fsm ! ClientFSM.Disconnected
    super.channelInactive(ctx)
  }

  override def channelRead0(ctx: ChannelHandlerContext, event: XMLEvent) {
    event match {
      case e: StartDocument =>
      case e: StartElement if depth == 0 =>
        depth += 1
        val element = getXmlElement(e)
        fsm ! element
      case e: StartElement if depth >= 1 =>
        val element = getXmlElement(e)
        if (nodes.nonEmpty) {
          val parent = nodes.head
          parent.children = element :: parent.children
        }
        nodes.push(element)
        depth += 1
      case e: EndElement =>
        depth -= 1
        if (nodes.nonEmpty) {
          val element = nodes.pop()
          if (depth == 1) {
            fsm ! element
          }
        }
      case e: Characters =>
        if (!e.isWhiteSpace) {
          nodes.head.body = e.getData
        }
      case e: EndDocument =>
      case _ =>
        logger.warning("Got unsupported event: " + event.getClass.getName)
    }
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
