package com.scxmpp.modules

import akka.actor._
import akka.event.LoggingReceive
import com.scxmpp.server.ServerContext
import com.typesafe.config.Config

case class RegisterHandler(name: String, clazz: Class[_])

/**
  * This module owns handler actors which are instantiated by modules
  */
class HandlerManager(serverContext: ServerContext, config: Config) extends Actor with ActorLogging {
  def receive = LoggingReceive {
    case RegisterHandler(name, clazz) =>
      val actorRef = context.actorOf(Props(clazz, serverContext, config).withDeploy(Deploy.local), name)
      sender ! actorRef
  }
}
