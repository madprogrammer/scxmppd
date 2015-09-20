package main.scala

import io.netty.channel.ChannelHandlerContext
import akka.actor._

object ClientFSM {
  // Possible states of the FSM
  sealed trait State
  case object WaitForStream extends State
  case object WaitForAuth extends State
  case object WaitForFeatureRequest extends State
  case object WaitForBind extends State
  case object WaitForSession extends State
  case object SessionEstablished extends State

  // Internal state (business logic)
  sealed trait Data
  case object Uninitialized extends Data
  case class ClientState(ip: String, port: Short)

  // Accepted commands
  case object ParseError
}

class ClientFSM(ctx: ChannelHandlerContext) extends FSM[ClientFSM.State, ClientFSM.Data]  {
  import ClientFSM._
  startWith(WaitForStream, Uninitialized)
  when(WaitForStream) {
    case Event(XmlElement("stream",List(("xmlns","http://etherx.jabber.org/streams"), ("from", _), ("to", _), ("version", "1.0"),
      ("xml:lang", "en")), "", List()), Uninitialized) =>
      goto(WaitForAuth) using Uninitialized
  }
  when(WaitForAuth) {
    case Event(XmlElement(_, _, _, _), Uninitialized) =>
      stay
  }
  whenUnhandled {
    case Event(ParseError, _) =>
      println("Got parse error")
      stay
    case Event(e: XmlElement, _) =>
      ctx.writeAndFlush(e);
      stay
  }
  onTransition {
    case WaitForStream -> WaitForAuth => println("WFS -> WFA")
  }
}
