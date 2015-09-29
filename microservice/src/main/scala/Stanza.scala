package main.scala

import shapeless._
import syntax.std.tuple._
import java.net.URLEncoder

object nullToEmptyString extends Poly1 {
  implicit def caseString = at[String](s => if (s != null) s else "")
}

object JID {

  def apply(user: String, server: String, resource: String): JID =
    new JID(StringPrep.nodePrep(user),
      StringPrep.namePrep(server),
      StringPrep.resourcePrep(resource))

  def apply(jid: String): JID = {
    val function = (user: String, server: String, resource: String) =>
      apply(user, server, resource)
    function.tupled(unapply(jid).get)
  }

  def unapply(jid: String): Option[(String, String, String)] = {
    val pattern = "^(?:([^ @]+)@)?([^/ ]+)(?:/([^/ ]+))?$".r
    pattern.findFirstMatchIn(jid) match {
      case None => None
      case Some(m) =>
        Some((m.group(1), m.group(2), m.group(3)) map nullToEmptyString)
    }
  }

  def unapply(jid: JID): Option[(String, String, String)] = {
    Some(jid.user, jid.server, jid.resource)
  }
}

class JID private (val user: String, val server: String, val resource: String) {
  def copy(
    user: String = this.user,
    server: String = this.server,
    resource: String = this.resource): JID =
    new JID(user, server, resource)

  def equals(that: JID): Boolean =
    this.user.equals(that.user) &&
    this.server.equals(that.server) &&
    this.resource.equals(that.resource)

  def withoutResource: JID = {
    new JID(user, server, "")
  }

  def toActorPath = URLEncoder.encode(user) + ":" + server + ":" +
    (if (!resource.isEmpty) URLEncoder.encode(resource) else "*")

  override def toString =
    (if (!user.isEmpty) user + "@" else "") +
    server +
    (if (!resource.isEmpty) "/" + resource else "")
}

object XmppNS {
  val Bind = "urn:ietf:params:xml:ns:xmpp-bind"
  val Session = "urn:ietf:params:xml:ns:xmpp-session"
  val Stanzas = "urn:ietf:params:xml:ns:xmpp-stanzas"
  val Stream = "http://etherx.jabber.org/streams"
  val Streams = "urn:ietf:params:xml:ns:xmpp-streams"
  val Sasl = "urn:ietf:params:xml:ns:xmpp-sasl"
}

object StreamHeader {
  def apply(id: String): XmlElement = {
    XmlElement("stream:stream", List(
      ("xmlns", "jabber:client"),
      ("xmlns:stream", XmppNS.Stream),
      ("version", "1.0"),
      ("lang", "en"),
      ("id", id),
      ("from", "localhost")), "", List())
  }
}

object StreamFeatures {
  def apply(authenticated: Boolean) = {
    authenticated match {
      case false =>
        XmlElement("stream:features", List(), "", List(
          XmlElement("mechanisms", List(("xmlns", XmppNS.Sasl)), "", List(
            XmlElement("mechanism", List(), "PLAIN", List())
          ))
        ))
      case true =>
        XmlElement("stream:features", List(), "", List(
          XmlElement("bind", List(("xmlns", XmppNS.Bind)), "", List()),
          XmlElement("session", List(("xmlns", XmppNS.Session)), "", List())))
    }
  }
}

object IQ {
  def apply(id: String, iqtype: String, body: XmlElement): XmlElement = {
    XmlElement("iq", List(("id", id), ("type", iqtype)), "", List(body))
  }
}

object Sasl {
  val Success = XmlElement("success", List(("xmlns", XmppNS.Sasl)), "", List())
  val Failure = XmlElement("failure", List(("xmlns", XmppNS.Sasl)), "", List(
    XmlElement("not-authorized", List(), "", List())))
}

object StreamError {
  val HostUnknown = "host-unknown"
  val InvalidNamespace = "invalid-namespace"
  val InvalidFrom = "invalid-from"
  val UnsupportedVersion = "unsupported-version"
  val XmlNotWellFormed = "xml-not-well-formed"

  def apply(error: String): XmlElement = {
    XmlElement("stream:error", List(), "", List(XmlElement(error, List(("xmlns", XmppNS.Streams)), "", List())))
  }
}

object StanzaError {
  val BadRequest = ("400", "modify", "bad-request")
  val Conflict = ("409", "cancel", "conflict")
  val NotImplemented = ("501", "cancel", "feature-not-implemented")
  val Forbidden = ("403", "auth", "forbidden")
  val InternalError = ("500", "wait", "internal-server-error")
  val ServiceUnavailable = ("503", "cancel", "service-unavailable")
  val ServiceUnavailableWait = ("502", "wait", "service-unavailable")

  private def getSubtag(code: String, etype: String, condition: String): XmlElement = {
    XmlElement("error", List(("code", code), ("type", etype)), "", List(
      XmlElement(condition, List(("xmlns", XmppNS.Stanzas)), "", List())))
  }

  def apply(element: XmlElement, error: (String, String, String)): XmlElement = {
    val from = element("from")
    val to = element("to")
    val attrs = element.attrs.map {
      case ("from", _) => ("from", to.get)
      case ("to", _) => ("to", from.get)
      case ("type", _) => ("type", "error")
      case (key, value) => (key, value)
    }
    XmlElement(element.name, attrs, element.body,
      getSubtag(error._1, error._2, error._3) :: element.children)
  }
}
