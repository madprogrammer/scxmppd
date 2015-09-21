package main.scala

import java.net.InetSocketAddress
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
    authenticated: Boolean = false) extends Data

  // Accepted commands
  case object ParseError
}

class ClientFSM(context: MicroserviceContext, ctx: ChannelHandlerContext) extends FSM[ClientFSM.State, ClientFSM.Data]  {
  import ClientFSM._

  val (ip, port) = ctx.channel.remoteAddress match { case s: InetSocketAddress => (s.getAddress.getHostAddress, s.getPort) }

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
                  case true => goto(WaitForBind) using data
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
      // TODO: Authentication
      ctx.writeAndFlush(XmlElement("success", List(("xmlns", XmppNS.Sasl)), "", List()))
      goto(WaitForStream) using ClientState(data.ip, data.port, RandomUtils.randomDigits(10), true)
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
                  ctx.writeAndFlush(IQ(id, "result",
                    XmlElement("bind", List(("xmlns", XmppNS.Bind)), "", List(
                      XmlElement("jid", List(), JID("Test", "Localhost", "RES").toString, List())))))
                    stay
                case _ =>
                  ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
                  stay
              }
            case _ =>
              ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
              stay
          }
        case _ => // no id
          ctx.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
          stay
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay
  }
  whenUnhandled {
    case Event(ParseError, _) =>
      streamError(StreamError.XmlNotWellFormed)
      stop
    case Event(e: XmlElement, _) =>
      ctx.writeAndFlush(e);
      stay
  }
  onTransition {
    case WaitForFeatureRequest -> WaitForStream =>
      ctx.pipeline.get("xmlFrameDecoder").asInstanceOf[XmlFrameDecoder].reset
      ctx.pipeline.get("handler").asInstanceOf[ServerHandler].reset
    case from -> to => println("Transition from " + from + " to " + to)
  }
}
