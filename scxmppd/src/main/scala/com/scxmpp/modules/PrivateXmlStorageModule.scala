package com.scxmpp.modules

import akka.event.LoggingReceive
import akka.util.Timeout
import com.scxmpp.akka.CustomDistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.modules.support.ModuleActor

import com.scxmpp.routing.{NotInterested, Route}
import com.scxmpp.server.ServerContext
import com.scxmpp.util.Helpers
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.{IQ, JID, StanzaError}
import com.typesafe.config.Config

import scala.concurrent.{Promise, Future}
import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._

import scala.pickling.Defaults._
import scala.pickling.json._

object PrivateXmlStorageModuleDefinitions
{
  val NS_PRIVATE: String = "jabber:iq:private"
}

/**
  * This module implements XEP-0049 Private XML Storage
  */
class PrivateXmlStorageModule(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  registerHandler(s"iq.${PrivateXmlStorageModuleDefinitions.NS_PRIVATE}", classOf[PrivateXmlStorageIqHandler])

  def receive = {
    case _ =>
  }

}

class PrivateXmlStorageIqHandler(serverContext: ServerContext, config: Config)
  extends ModuleActor(serverContext, config) {

  mediator ! Subscribe(Topics.ModulesLoaded, self)

  private def jidToKey(jid: JID): String = Helpers.urlEncode(jid.user) + ":" + jid.server + ":private"

  private def storePrivateXml(jid: JID, xml: XmlElement): Future[Boolean] = {
    implicit val ec = context.dispatcher
    val promise = Promise[Boolean]
    xml("xmlns") match {
      case None =>
        promise.failure(new IllegalArgumentException("xmlns is null"))
        promise.future
      case Some(xmlns) =>
        serverContext.keyValueStore.setValue(s"%s/%s".format(jidToKey(jid),
          Helpers.urlEncode(xml("xmlns").get)), xml.pickle.value)
    }
  }

  private def getPrivateXml(jid: JID, xml: XmlElement): Future[String] = {
    implicit val ec = context.dispatcher
    val promise = Promise[String]
    xml("xmlns") match {
      case None =>
        promise.failure(new IllegalArgumentException("xmlns is null"))
        promise.future
      case Some(xmlns) =>
        serverContext.keyValueStore.getValue(s"%s/%s".format(jidToKey(jid),
          Helpers.urlEncode(xml("xmlns").get)))
    }
  }

  private def futureToFutureTry[T](future: Future[T]): Future[Try[T]] = {
    implicit val ec = context.dispatcher
    val promise = Promise[Try[T]]()
    future onComplete promise.success
    promise.future
  }

  def receive = {
    case SubscribeAck(Subscribe(Topics.ModulesLoaded, None, `self`)) =>
      context become ready
  }

  def ready = LoggingReceive {
    case Route(from, to, msg @ XmlElement("iq", _, _, _)) =>
      (msg("id"), msg("type")) match {
        case (Some(id), Some("set")) =>
          msg.child("query") match {
            case Some(query) =>
              if (query("xmlns").contains(PrivateXmlStorageModuleDefinitions.NS_PRIVATE)) {
                implicit val ec = context.dispatcher
                val originalSender = sender
                val futures = query.children.map(storePrivateXml(to, _))
                Future.sequence(futures) onComplete {
                  case Failure(cause) =>
                    logger.warning("Failed to save one or more of the private XML tags " + msg)
                    originalSender ! Route(to, from, IQ(id, "error", StanzaError(query, StanzaError.ServiceUnavailable)))
                  case Success(results) =>
                    originalSender ! Route(to, from, IQ(id, "result"))
                }
              }
            case _ =>
          }
        case (Some(id), Some("get")) =>
          msg.child("query") match {
            case Some(query) =>
              if (query("xmlns").contains(PrivateXmlStorageModuleDefinitions.NS_PRIVATE)) {
                implicit val ec = context.dispatcher
                val originalSender = sender
                val futures = query.children.map(getPrivateXml(to, _))
                val futuretries = futures.map(futureToFutureTry)
                Future.sequence(futuretries) onSuccess {
                  case results =>
                    val elements = (query.children, results).zipped.map((e: XmlElement, r: Try[String]) => {
                      if(r.isFailure) XmlElement(e.name, e.attrs, "", List()) else r.get.unpickle[XmlElement]
                    })
                    val newQuery = XmlElement("query",
                      List(("xmlns", PrivateXmlStorageModuleDefinitions.NS_PRIVATE)), "", elements)
                    originalSender ! Route(to, from, IQ(id, "result", newQuery))
                }
              }
            case _ =>
          }
        case _ =>
      }
  }

}