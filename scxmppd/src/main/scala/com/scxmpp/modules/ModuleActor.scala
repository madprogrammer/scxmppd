package com.scxmpp.modules

import akka.actor._
import com.scxmpp.akka.CustomDistributedPubSubExtension
import com.scxmpp.server.ServerContext

abstract class ModuleActor(serverContext: ServerContext) extends Actor {
  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val router = context.actorSelection("/user/router")
}
