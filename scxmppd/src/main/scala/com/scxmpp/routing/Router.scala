package com.scxmpp.routing

import com.scxmpp.akka.{CustomDistributedPubSubExtension, CustomDistributedPubSubMediator}
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.modules.ModuleActor
import com.scxmpp.pipeline.PipelineHandler
import com.scxmpp.xml.XmlElement
import com.scxmpp.xmpp.JID
import com.scxmpp.server.ServerContext

import com.typesafe.config.Config

import scala.util.{Success, Failure}
import scala.collection.immutable
import scala.collection.JavaConversions._
import akka.event.LoggingReceive
import akka.actor._
import java.util.logging.Logger

case class Route(from: JID, to: JID, element: XmlElement)

class Router(serverContext: ServerContext, config: Config) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.{SendToAll, Publish}
  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val logger = Logger.getLogger(getClass.getName)
  val pipeline = constructPipeline
  val modules = loadModules

  def constructPipeline: immutable.ListMap[String, PipelineHandler] = {
    immutable.ListMap((for (
      name <- config.getStringList("routing.pipeline");
      clazz = serverContext.dynamicAccess.createInstanceFor[PipelineHandler](name, immutable.Seq.empty).get
    ) yield clazz.name -> clazz): _*)
  }

  def loadModules: immutable.List[ActorRef] = {
    for (
      name <- config.getStringList("routing.modules").toList;
      clazz = serverContext.dynamicAccess.getClassFor[ModuleActor](name).get
    ) yield context.system.actorOf(Props(clazz, serverContext, config))
  }

  def receive = LoggingReceive {
    case route @ Route(from, to, element) =>
      pipeline.values.foldLeft[Option[Route]](Some(route)) { case (acc, handler) => handler.handle(route, acc) } match {
        case Some(result) =>
          mediator ! SendToAll("/user/c2s/%s".format(to.toActorPath), result, allButSelf = false)
          mediator ! Publish(Topics.MessageRouted, Hooks.MessageRouted(result))
        case None =>
          logger.info("Message %s discarded after pipeline processing".format(route))
      }
    }
}
