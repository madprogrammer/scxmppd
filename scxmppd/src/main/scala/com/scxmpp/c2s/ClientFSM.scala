package com.scxmpp.c2s

import java.util.logging.Logger
import java.net.InetSocketAddress
import javax.xml.bind.DatatypeConverter
import com.scxmpp.akka.{CustomDistributedPubSubMediator, CustomDistributedPubSubExtension}
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.netty.XmlFrameDecoder
import com.scxmpp.routing.Route
import com.scxmpp.server.{ServerHandler, ServerContext}
import com.scxmpp.util.{RandomUtils, StringPrep}
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp._
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
    streamId: String,
    authenticated: Boolean = false,
    user: String = "",
    server: String = "",
    resource: String = "",
    jid: Option[JID] = None
  ) extends Data

  // Accepted commands
  case object Disconnected
  case object ParseError
  case object Initialize
  case class Replaced(ref: ActorRef)
  case class ExceptionCaught(e: Throwable)
}

class ClientFSM(
  serverContext: ServerContext,
  channelContext: ChannelHandlerContext,
  state: ClientFSM.State,
  data: ClientFSM.Data
) extends FSM[ClientFSM.State, ClientFSM.Data]  {
  import CustomDistributedPubSubMediator.Publish
  import ClientFSM._

  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val (ip, port) = channelContext.channel.remoteAddress match { case s: InetSocketAddress => (s.getAddress.getHostAddress, s.getPort) }
  val logger = Logger.getLogger(getClass.getName)
  val router = context.actorSelection("/user/router")

  def streamError(error: String): State = {
    channelContext.writeAndFlush(StreamError(error))
    channelContext.writeAndFlush("</stream:stream>")
    channelContext.close
    stop()
  }

  def streamHeaderError(id: String, error: String): State = {
    channelContext.writeAndFlush(StreamHeader(id))
    streamError(error)
  }


  def checkFrom(from: String, jid: JID): Boolean = {
    from match {
      case JID(user, server, rsrc) =>
        user == jid.user && server == jid.server && rsrc == jid.resource
      case _ =>
        false
    }
  }

  def replaceFromTo(from: JID, to: JID, element: XmlElement): XmlElement = {
    element.setAttr("from", from.toString).setAttr("to", to.toString)
  }

  startWith(state, data)
  when(WaitForStream) {
    case Event(e @ XmlElement("stream", _, "", List()), data: ClientState) =>
      e("xmlns") match {
        case Some(XmppNS.Stream) =>
          val server = StringPrep.namePrep(e("to") getOrElse "")
          if (!(serverContext.xmpp.hosts contains server))
            streamHeaderError(data.streamId, StreamError.HostUnknown)
          else
            e("version") match {
              case Some("1.0") =>
                channelContext.writeAndFlush(StreamHeader(data.streamId))
                channelContext.writeAndFlush(StreamFeatures(data.authenticated))
                data.authenticated match {
                  case false => goto(WaitForFeatureRequest) using data
                  case true => goto(WaitForBind) using data.copy(server = server)
                }
              case _ =>
                streamHeaderError(data.streamId, StreamError.UnsupportedVersion)
            }
        case _ =>
          streamHeaderError(data.streamId, StreamError.InvalidNamespace)
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay()
  }
  when(WaitForFeatureRequest) {
    case Event(e @ XmlElement("auth", _, auth, List()), data: ClientState) =>
      val decoded = new String(DatatypeConverter.parseBase64Binary(auth))
      decoded.split("\u0000") match {
        case Array(_, user, pass) =>
          channelContext.writeAndFlush(Sasl.Success)
          goto(WaitForStream) using data.copy(
            streamId = RandomUtils.randomDigits(10),
            user = StringPrep.nodePrep(user),
            authenticated = true)
        case _ =>
          channelContext.writeAndFlush(Sasl.Failure)
          channelContext.writeAndFlush("</stream:stream>")
          channelContext.close
          stop()
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay()
  }
  when(WaitForBind) {
    case Event(e @ XmlElement("iq", _, _, _), data: ClientState) =>
      e("id") match {
        case Some(id) =>
          e.child("bind") match {
            case Some(bind @ XmlElement("bind", List("xmlns" -> XmppNS.Bind), _, _)) =>
              bind.child("resource") match {
                case Some(rsrc @ XmlElement("resource", _, resource, List())) =>
                  val resprep = StringPrep.resourcePrep(resource)
                  val jid = JID(data.user, data.server, resprep)
                  channelContext.writeAndFlush(IQ(id, "result",
                    XmlElement("bind", List("xmlns" -> XmppNS.Bind), "", List(
                      XmlElement("jid", List(), jid.toString, List())))))
                    goto(WaitForSession) using data.copy(resource = resprep, jid = Some(jid))
                case _ =>
                  channelContext.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
                  stay()
              }
            case _ =>
              channelContext.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
              stay()
          }
        case _ =>
          channelContext.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
          stay()
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay()
  }
  when(WaitForSession) {
    case Event(e @ XmlElement("iq", _, _, _), data: ClientState) =>
      e("id") match {
        case Some(id) =>
          e.child("session") match {
            case Some(XmlElement("session", List("xmlns" -> XmppNS.Session), "", List())) =>
              channelContext.writeAndFlush(IQ(id, "result",
                XmlElement("session", List("xmlns" -> XmppNS.Session), "", List())))
              goto(SessionEstablished) using data
            case _ =>
              stay()
          }
        case _ =>
          channelContext.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
          stay()
      }
    case Event(XmlElement(_, _, _, _), _) =>
      stay()
  }
  when(SessionEstablished) {
    // Event arrived from client connection
    case Event(e @ XmlElement(name, _, _, _), data: ClientState) =>
      val newEl = e.removeAttr("xmlns")
      e("from") match {
        case None =>
        case Some(from) =>
          if (!checkFrom(from, data.jid.get))
            streamError(StreamError.InvalidFrom)
      }
      val toJID = e("to") match {
        case None => data.jid.get.withoutResource
        case Some(to) => JID(to)
      }
      name match {
        case "presence" =>
          // TODO: don't ignore presence
        case "iq" | "message" =>
          router ! Route(data.jid.get, toJID, newEl)
        case _ =>
      }
      stay()
    // Event addressed to client
    case Event(Route(from, to, e @ XmlElement(name, _, _, _)), data: ClientState) =>
      name match {
        case "message" | "iq" =>
          channelContext.writeAndFlush(replaceFromTo(from, to, e))
        case _ =>
      }
      stay()
    case Event(Initialize, data: ClientState) =>
      mediator ! Publish(Topics.SessionOpened, Hooks.SessionOpened(data.jid.get, self))
      stay()
  }
  whenUnhandled {
    case Event(ParseError, _) =>
      streamError(StreamError.XmlNotWellFormed)
      stop()
    case Event(ExceptionCaught(e), _) =>
      stop()
    case Event(Disconnected, _) =>
      logger.info("Disconnected: " + self.path)
      stop()
    case Event(Replaced(ref), _) =>
      logger.info("FSM replaced by " + ref.path)
      stop()
    case Event(e @ XmlElement, _) =>
      logger.warning("Unhandled XmlElement: " + e)
      stay()
  }
  onTransition {
    case WaitForFeatureRequest -> WaitForStream =>
      channelContext.pipeline.get("xmlFrameDecoder").asInstanceOf[XmlFrameDecoder].reset()
      channelContext.handler.asInstanceOf[ServerHandler].reset()
    case WaitForSession -> SessionEstablished =>
      channelContext.handler.asInstanceOf[ServerHandler].replaceFSM(
        channelContext,
        SessionEstablished,
        stateData.asInstanceOf[ClientState])
    case from -> to => println("Transition from " + from + " to " + to)
  }
}
