package com.scxmpp.modules

import akka.actor._
import akka.util.Timeout
import akka.pattern.ask

import com.scxmpp.akka.CustomDistributedPubSubExtension
import com.scxmpp.server.ServerContext

import com.typesafe.config.Config
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

abstract class ModuleActor(serverContext: ServerContext, config: Config) extends Actor {
  lazy val mediator = CustomDistributedPubSubExtension(context.system).mediator
  lazy val router = context.actorSelection("/user/router")
  lazy val handlerManager = context.actorSelection("/user/handler")

  // TODO: Limit allowed types to subclases of Actor
  def registerHandler(name: String, clazz: Class[_]): ActorRef = {
    implicit val timeout = Timeout(60 seconds)
    val future = handlerManager ? RegisterHandler(name, clazz)
    Await.result(future, timeout.duration).asInstanceOf[ActorRef]
  }
}
