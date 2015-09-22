package main.scala

import java.util.logging.Logger
import java.net.InetSocketAddress
import javax.xml.bind.DatatypeConverter
import io.netty.channel.ChannelHandlerContext
import akka.actor._

object ClientFSM {
  // Possible states of the FSM
  sealed trait State
  case object WaitForStream extends State
  case object WaitForFeatureRequest extends State
  case object WaitForBind extends State
  case object WaitForSession extends State
  case object SessionEstablished extends State

  // Internal state (business logic)
  sealed trait Data
  case class ClientState(
    ip: String,
    port: Int,
    streamId: String,
    authenticated: Boolean = false,
    user: String = "",
    server: String = "",
    resource: String = ""
  ) extends Data

  // Accepted commands
  case object Disconnected
  case object ParseError
  case class ExceptionCaught(e: Throwable)
}

class ClientFSM(context: MicroserviceContext, ctx: ChannelHandlerContext) extends FSM[ClientFSM.State, ClientFSM.Data]  {
  import ClientFSM._

  val (ip, port) = ctx.channel.remoteAddress match { case s: InetSocketAddress => (s.getAddress.getHostAddress, s.getPort) }
  val logger = Logger.getLogger(getClass.getName)

  def streamError(error: String): State = {
    ctx.writeAndFlush(StreamError(error))
    ctx.writeAndFlush("</stream:stream>")
    ctx.close
    stop
  }

  def streamHeaderError(id: String, error: String): State = {
    ctx.writeAndFlush(StreamHeader(id))
    streamError(error)
  }

  startWith(WaitForStream, ClientState(ip, port, RandomUtils.randomDigits(10)))
  when(WaitForStream) {
    case Event(e @ XmlElement("stream", _, "", List()), data: ClientState) =>
      e("xmlns") match {
        case Some(XmppNS.Stream) =>
          val server = StringPrep.namePrep(e("to") getOrElse "")
          if (!(context.xmpp.hosts contains server))
            streamHeaderError(data.streamId, StreamError.HostUnknown)
          else
            e("version") match {
              case Some("1.0") =>
                ctx.writeAndFlush(StreamHeader(data.streamId))
                ctx.writeAndFlush(StreamFeatures(data.authenticated))
                data.authenticated match {
                  case false => goto(WaitForFeatureRequest) using data
                  case true => goto(WaitForBind) using data.copy(server = StringPrep.nodePrep(server))
                }
              case _ =>
                streamHeaderError(data.streamId, StreamError.UnsupportedVersion)
            }
        case _ =>
          streamHeaderError(data.streamId, StreamError.InvalidNamespace)
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay
  }
  when(WaitForFeatureRequest) {
    case Event(e @ XmlElement("auth", _, auth, List()), data: ClientState) =>
      val decoded = new String(DatatypeConverter.parseBase64Binary(auth))
      decoded.split("\u0000") match {
        case Array(_, user, pass) =>
          ctx.writeAndFlush(Sasl.Success)
          goto(WaitForStream) using data.copy(
            streamId = RandomUtils.randomDigits(10),
            user = StringPrep.namePrep(user),
            authenticated = true)
        case _ =>
          ctx.writeAndFlush(Sasl.Failure)
          ctx.writeAndFlush("</stream:stream>")
          ctx.close
          stop
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay
  }
  when(WaitForBind) {
    case Event(e @ XmlElement("iq", _, _, _), data: ClientState) =>
      e("id") match {
        case Some(id) =>
          e.child("bind") match {
            case Some(bind @ XmlElement("bind", List(("xmlns", XmppNS.Bind)), _, _)) =>
              bind.child("resource") match {
                case Some(rsrc @ XmlElement("resource", _, resource, List())) =>
                  val resprep = StringPrep.resourcePrep(resource)
                  ctx.writeAndFlush(IQ(id, "result",
                    XmlElement("bind", List(("xmlns", XmppNS.Bind)), "", List(
                      XmlElement("jid", List(), JID(data.user, data.server, resprep).toString, List())))))
                    goto(WaitForSession) using data.copy(resource = resprep)
                case _ =>
                  ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
                  stay
              }
            case _ =>
              ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
              stay
          }
        case _ =>
          ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
          stay
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay
  }
  when(WaitForSession) {
    case Event(e @ XmlElement("iq", _, _, _), data: ClientState) =>
      e("id") match {
        case Some(id) =>
          e.child("session") match {
            case Some(XmlElement("session", List(("xmlns", XmppNS.Session)), "", List())) =>
              ctx.writeAndFlush(IQ(id, "result",
                XmlElement("session", List(("xmlns", XmppNS.Session)), "", List())))
              goto(SessionEstablished) using data
            case _ =>
              stay
          }
        case _ =>
          ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
          stay
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay
  }
  when(SessionEstablished) {
    case Event(XmlElement(_, _, _, _), _) =>
      stay
  }
  whenUnhandled {
    case Event(ParseError, _) =>
      streamError(StreamError.XmlNotWellFormed)
      stop
    case Event(ExceptionCaught(e), _) =>
      stop
    case Event(Disconnected, _) =>
      logger.info("Disconnected: " + self.path.name)
      stop
    case Event(e @ XmlElement, _) =>
      logger.warning("Unhandled XmlElement: " + e)
      stay
  }
  onTransition {
    case WaitForFeatureRequest -> WaitForStream =>
      ctx.pipeline.get("xmlFrameDecoder").asInstanceOf[XmlFrameDecoder].reset
      ctx.pipeline.get("handler").asInstanceOf[ServerHandler].reset
    case from -> to => println("Transition from " + from + " to " + to)
  }
}
