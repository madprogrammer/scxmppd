package com.scxmpp.modules

import akka.actor._
import akka.event.LoggingReceive
import com.scxmpp.server.ServerContext
import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.collection.immutable

class ModuleManager(serverContext: ServerContext, config: Config) extends Actor with ActorLogging {

  val modules = loadModules

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
