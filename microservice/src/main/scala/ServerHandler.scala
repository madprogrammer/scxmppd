package main.scala

import java.util.logging.Logger
import io.netty.handler.codec.DecoderException
import io.netty.channel.{Channel, ChannelHandlerContext, SimpleChannelInboundHandler}
import java.net.InetSocketAddress
import java.net.URLEncoder
import javax.xml.stream.events._
import scala.collection.mutable.Stack
import scala.collection.JavaConversions._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class ServerHandler(context: MicroserviceContext, manager: ActorRef) extends SimpleChannelInboundHandler[XMLEvent] {

  val logger = Logger.getLogger(getClass.getName)
  var nodes: Stack[XmlElement] = Stack()
  var fsm: ActorRef = _
  var depth = 0

  def getAttributeTuple(attr: Attribute): (String, String) = {
    return (
      attr.getName.getPrefix match {
        case prefix: String if prefix.length > 0 =>
          prefix + ":" + attr.getName.getLocalPart
        case prefix: String =>
          attr.getName.getLocalPart
      },
      attr.getValue
    )
  }

  def getXmlElement(e: StartElement): XmlElement = {
    val ns = e.getName.getNamespaceURI
    val element = XmlElement(
      e.getName.getLocalPart,
      ns match {
        case uri: String if uri.length > 0 =>
          ("xmlns", ns) :: e.getAttributes.map(x => getAttributeTuple(x.asInstanceOf[Attribute])).toList
        case uri: String =>
          e.getAttributes.map(x => getAttributeTuple(x.asInstanceOf[Attribute])).toList
      },
      "", List())
    return element
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
        val name = Array(
          URLEncoder.encode(jid.user),
          jid.server,
          URLEncoder.encode(jid.resource)
        ).mkString(":")
        val previous = fsm
        val future = manager ? CreateClientFSM(ctx, name, state, data)
        fsm = Await.result(future, timeout.duration).asInstanceOf[ActorRef]
        previous ! ClientFSM.Replaced(fsm)
    }
  }

  def reset() {
    depth = 0
    nodes.clear
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
        logger.warning(element.toString)
        fsm ! element
      case e: StartElement if depth >= 1 =>
        val element = getXmlElement(e)
        if (nodes.length > 0) {
          val parent = nodes(0)
          parent.children = element :: parent.children
        }
        nodes.push(element)
        depth += 1
      case e: EndElement =>
        depth -= 1
        if (nodes.length > 0) {
          val element = nodes.pop
          if (depth == 1) {
            logger.warning(element.toString)
            fsm ! element
          }
        }
      case e: Characters =>
        if (!e.isWhiteSpace) {
          nodes(0).body = e.getData
        }
      case e: EndDocument =>
      case _ =>
        logger.warning("Got unsupported event: " + event.getClass.getName)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    logger.warning("Unexpected exception: " + cause)
    cause.printStackTrace
    cause match {
      case e: DecoderException =>
        fsm ! ClientFSM.ParseError
      case e =>
        fsm ! ClientFSM.ExceptionCaught(e)
    }
  }

}
