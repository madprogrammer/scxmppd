import java.util.logging.Logger
import javax.net.ssl.{SSLContext, SSLEngine}
import io.netty.handler.ssl.SslHandler
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import javax.xml.stream.events._
import scala.collection.mutable.Stack
import scala.collection.JavaConversions._
import main.scala.XmlElement

class ServerHandler(sslContext: SSLContext) extends SimpleChannelInboundHandler[XMLEvent] {

  val logger = Logger.getLogger(getClass.getName)
  var nodes: Stack[XmlElement] = Stack()
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

  override def channelActive(ctx: ChannelHandlerContext) {
    super.channelActive(ctx)
    val engine = sslContext.createSSLEngine
    engine.setUseClientMode(false)
    ctx.channel.pipeline.addFirst("ssl", new SslHandler(engine))
  }

  override def channelRead0(ctx: ChannelHandlerContext, event: XMLEvent) {
    event match {
      case e: StartDocument =>
      case e: StartElement if depth == 0 =>
        depth += 1
        val element = getXmlElement(e)
        logger.warning(element.toString)
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
          }
        }
      case e: EndDocument =>
      case e: Characters =>
        if (!e.isWhiteSpace) {
          nodes(0).body = e.getData
        }
      case _ =>
        logger.warning("Got unsupported event: " + event.getClass.getName)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    logger.warning("Unexpected exception: " + cause)
    ctx.close
  }

}
