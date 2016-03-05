package com.scxmpp.modules

import akka.event.LoggingReceive
import com.scxmpp.akka.CustomDistributedPubSubMediator
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.modules.support.ModuleActor
import com.scxmpp.routing.Route
import com.scxmpp.server.ServerContext
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.IQ

import com.typesafe.config.Config

import scala.collection.mutable.ArrayBuffer

object DiscoveryModuleDefinitions
{
  val NS_DISCO: String = "http://jabber.org/protocol/disco#info"
}

class DiscoveryModule(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  registerHandler(s"iq.${DiscoveryModuleDefinitions.NS_DISCO}", classOf[DiscoveryIqHandler])

  def receive = {
    case _ =>
  }
}

class DiscoveryIqHandler(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}
  val features = new ArrayBuffer[String]()

  mediator ! Subscribe(Topics.DiscoveryFeature, self)

  def getFeatureTagList: List[XmlElement] = {
    features.map((s: String) => XmlElement("feature", List(("var", s)), "", List())).toList
  }

  def receive = {
    case SubscribeAck(Subscribe(Topics.DiscoveryFeature, None, `self`)) =>
      context become ready
  }

  def ready = LoggingReceive {
    case Hooks.DiscoveryFeature(feature) =>
      features.append(feature)
    case Route(from, to, msg @ XmlElement("iq", _, _, _)) =>
      if (config.getStringList("xmpp.hosts") contains to.toString) {
        (msg("id"), msg("type")) match {
          case (Some(id), Some("get")) =>
            msg.child("query") match {
              case Some(query) =>
                if (query("xmlns").contains(DiscoveryModuleDefinitions.NS_DISCO)) {
                  sender ! Route(to, from, IQ(id, "result", XmlElement("query",
                    List(("xmlns", DiscoveryModuleDefinitions.NS_DISCO)), "", getFeatureTagList)))
                }
              case _ =>
            }
          case _ =>
        }
      }
    case _ =>
  }

}