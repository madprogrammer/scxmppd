package main.scala

import akka.actor._

object ClientFSM {
  sealed trait State
  case object WaitForStream extends State
  case object WaitForAuth extends State
  case object WaitForFeatureRequest extends State
  case object WaitForBind extends State
  case object WaitForSession extends State
  case object SessionEstablished extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class ClientState(ip: String, port: Short)
}

class ClientFSM extends FSM[ClientFSM.State, ClientFSM.Data]  {
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
  onTransition {
    case WaitForStream -> WaitForAuth => println("WFS -> WFA")
  }
}
