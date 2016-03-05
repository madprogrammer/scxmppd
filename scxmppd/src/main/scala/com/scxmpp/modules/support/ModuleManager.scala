package com.scxmpp.modules.support

import akka.actor._
import akka.event.LoggingReceive
import com.scxmpp.akka.CustomDistributedPubSubExtension
import com.scxmpp.akka.CustomDistributedPubSubMediator.Publish
import com.scxmpp.hooks.{Hooks, Topics}
import com.scxmpp.server.ServerContext
import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.collection.immutable

class ModuleManager(serverContext: ServerContext, config: Config) extends Actor with ActorLogging {

  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val modules = loadModules

  mediator ! Publish(Topics.ModulesLoaded, Hooks.ModulesLoaded, sendOneMessageToEachGroup = false, onlyLocal = true)

  def loadModules: immutable.List[ActorRef] = {
    for (
      name <- config.getStringList("routing.modules").toList;
      clazz = serverContext.dynamicAccess.getClassFor[ModuleActor](name).get
    ) yield context.actorOf(Props(clazz, serverContext, config), name)
  }

  def receive = LoggingReceive {
    case _ =>
  }
}
