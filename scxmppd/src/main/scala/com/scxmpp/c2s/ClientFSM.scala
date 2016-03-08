package com.scxmpp.c2s

import java.util.logging.Logger
import java.net.InetSocketAddress
import javax.xml.bind.DatatypeConverter
import com.scxmpp.akka.{CustomDistributedPubSubMediator, CustomDistributedPubSubExtension}
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.netty.{XmlElementDecoder, XmlFrameDecoder}
import com.scxmpp.routing.Route
import com.scxmpp.util.{RandomUtils, StringPrep}
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp._
import com.typesafe.config.Config
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
  case class ClientState(
    streamId: String,
    authenticated: Boolean = false,
    user: String = "",
    server: String = "",
    resource: String = "",
    jid: Option[JID] = None,
    bosh: Boolean = false,
    context: ChannelHandlerContext
  )

  // Accepted commands
  case object Disconnected
  case object ParseError
  case object Initialize
  case class Replaced(ref: ActorRef)
  case class ExceptionCaught(e: Throwable)
}

class ClientFSM(
  config: Config,
  state: ClientFSM.State,
  data: ClientFSM.ClientState
) extends FSM[ClientFSM.State, ClientFSM.ClientState]  {
  import CustomDistributedPubSubMediator.Publish
  import ClientFSM._

  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val (ip, port) = data.context.channel.remoteAddress match { case s: InetSocketAddress => (s.getAddress.getHostAddress, s.getPort) }
  val logger = Logger.getLogger(getClass.getName)
  val router = context.actorSelection("/user/router")

  def streamError(error: String): State = {
    data.context.channel.writeAndFlush(StreamError(error))
    data.context.channel.writeAndFlush("</stream:stream>")
    data.context.close
    stop()
  }

  def streamHeaderError(id: String, error: String): State = {
    data.context.channel.writeAndFlush(StreamHeader(id))
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
          if (!(config.getStringList("xmpp.hosts") contains server))
            streamHeaderError(data.streamId, StreamError.HostUnknown)
          else
            e("version") match {
              case Some("1.0") =>
                if (!data.bosh)
                  data.context.channel.writeAndFlush(StreamHeader(data.streamId))
                data.context.channel.writeAndFlush(StreamFeatures(data.authenticated))
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
    case Event(e @ XmlElement(_, _, _, _), _) =>
      println(e)
      stay()
  }
  when(WaitForFeatureRequest) {
    case Event(e @ XmlElement("auth", _, auth, List()), data: ClientState) =>
      val decoded = new String(DatatypeConverter.parseBase64Binary(auth))
      decoded.split("\u0000") match {
        case Array(_, user, pass) =>
          data.context.channel.writeAndFlush(Sasl.Success)
          goto(WaitForStream) using data.copy(
            streamId = RandomUtils.randomDigits(10),
            user = StringPrep.nodePrep(user),
            authenticated = true)
        case _ =>
          data.context.channel.writeAndFlush(Sasl.Failure)
          data.context.channel.writeAndFlush("</stream:stream>")
          data.context.close
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
                  data.context.channel.writeAndFlush(IQ(id, "result",
                    XmlElement("bind", List("xmlns" -> XmppNS.Bind), "", List(
                      XmlElement("jid", List(), jid.toString, List())))))
                    goto(WaitForSession) using data.copy(resource = resprep, jid = Some(jid))
                case _ =>
                  data.context.channel.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
                  stay()
              }
            case _ =>
              data.context.channel.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
              stay()
          }
        case _ =>
          data.context.channel.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
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
              data.context.channel.writeAndFlush(IQ(id, "result",
                XmlElement("session", List("xmlns" -> XmppNS.Session), "", List())))
              goto(SessionEstablished) using data
            case _ =>
              stay()
          }
        case _ =>
          data.context.channel.writeAndFlush(StanzaError(e, StanzaError.BadRequest))
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
        case "iq" | "message" | "presence" =>
          router ! Route(data.jid.get, toJID, replaceFromTo(data.jid.get, toJID, newEl))
        case _ =>
      }
      stay()
    // Event addressed to client
    case Event(Route(from, to, e @ XmlElement(name, _, _, _)), data: ClientState) =>
      name match {
        case "iq" | "message" | "presence" =>
          data.context.channel.writeAndFlush(replaceFromTo(from, to, e))
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
    case Event(Disconnected, data: ClientState) =>
      logger.info("Disconnected: " + self.path)
      if (data.jid.isDefined)
        mediator ! Publish(Topics.SessionClosed, Hooks.SessionClosed(data.jid.get, self))
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
      data.context.pipeline.get(classOf[XmlFrameDecoder]).reset()
      data.context.pipeline.get(classOf[XmlElementDecoder]).reset()
    case WaitForSession -> SessionEstablished =>
      data.context.handler.asInstanceOf[XmppServerHandler].replaceFSM(
        data.context,
        SessionEstablished,
        stateData.asInstanceOf[ClientState])
    case from -> to => logger.info("Transition from " + from + " to " + to)
  }
}
