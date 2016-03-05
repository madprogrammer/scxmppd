package com.scxmpp.modules

import akka.util.Timeout
import com.scxmpp.akka.CustomDistributedPubSubMediator.Publish

import scala.concurrent.duration._
import scala.util.{Success, Failure}
import akka.event.LoggingReceive
import com.scxmpp.akka.CustomDistributedPubSubMediator
import com.scxmpp.hooks.Hooks
import com.scxmpp.hooks.Topics
import com.scxmpp.modules.support.ModuleActor
import com.scxmpp.routing.Route
import com.scxmpp.server.ServerContext
import com.scxmpp.util.Helpers
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.{StanzaError, JID, IQ}

import com.typesafe.config.Config

object LastActivityModuleDefinitions
{
  val NS_LAST: String = "jabber:iq:last"
}

class LastActivityModule(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  registerHandler(s"iq.${LastActivityModuleDefinitions.NS_LAST}", classOf[LastActivityIqHandler])

  def receive = {
    case _ =>
  }
}

class LastActivityIqHandler(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}

  mediator ! Subscribe(Topics.SessionClosed, self)

  private def jidToKey(jid: JID): String = Helpers.urlEncode(jid.user) + ":" + jid.server + ":last"

  def receive = {
    case SubscribeAck(Subscribe(Topics.SessionClosed, None, `self`)) =>
      mediator ! Publish(Topics.DiscoveryFeature, Hooks.DiscoveryFeature(LastActivityModuleDefinitions.NS_LAST),
        sendOneMessageToEachGroup = false, onlyLocal = true)
      context become ready
  }

  def ready = LoggingReceive {
    case Route(from, to, msg @ XmlElement("iq", _, _, _)) =>
      (msg("id"), msg("type")) match {
        case (Some(id), Some("get"))  =>
          msg.child("query") match {
            case Some(query) =>
              if (query("xmlns").contains(LastActivityModuleDefinitions.NS_LAST)) {
                implicit val ec = context.dispatcher
                implicit val timeout = Timeout(5.seconds)
                val originalSender = sender
                context.actorSelection("/user/c2s/%s".format(to.toActorPath)).resolveOne onComplete {
                  case Success(actorRef) =>
                    originalSender ! Route(to, from, IQ(id, "result",
                      XmlElement("query",
                        List(("xmlns", LastActivityModuleDefinitions.NS_LAST), ("seconds", "0")),
                        "", List())))
                  case Failure(_) =>
                    serverContext.keyValueStore.getValue(jidToKey(to)) onComplete {
                      case Success(ts) =>
                        val seconds = Helpers.unixTimestamp - ts.toLong
                        originalSender ! Route(to, from, IQ(id, "result",
                          XmlElement("query",
                            List(("xmlns", LastActivityModuleDefinitions.NS_LAST), ("seconds", seconds.toString)),
                            "", List())))
                      case Failure(_) =>
                        originalSender ! Route(to, from, StanzaError(msg, StanzaError.ServiceUnavailable))
                    }
                }
              }
            case _ =>
          }
        case _ =>
      }
    case Hooks.SessionClosed(jid, _) =>
      serverContext.keyValueStore.setValue(jidToKey(jid), Helpers.unixTimestamp.toString)
    case _ =>
  }

}