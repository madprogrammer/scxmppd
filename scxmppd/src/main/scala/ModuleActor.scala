package main.scala

import akka.actor._

abstract class ModuleActor(serverContext: ServerContext) extends Actor {
  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}

  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val router = context.actorSelection("/user/router")
}