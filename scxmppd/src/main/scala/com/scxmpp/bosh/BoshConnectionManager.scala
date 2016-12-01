package com.scxmpp.bosh

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import com.scxmpp.c2s.{ClientFSM, CreateClientFSM => ManagerCreateFSM}
import com.typesafe.config.Config

import com.scxmpp.akka.{CustomDistributedPubSubExtension, CustomDistributedPubSubMediator}
import com.scxmpp.hooks.{Hooks, Topics}

import scala.concurrent.duration._
import scala.collection.mutable

case class CreateClientFSM(sid: String, name: String, state: ClientFSM.State, data: ClientFSM.ClientState)
case class GetClientFSM(sid: String)

class BoshConnectionManager(config: Config) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.{Subscribe, SubscribeAck}

  val clients = mutable.HashMap.empty[String, String]
  val manager = context.actorSelection("/user/c2s")
  val mediator = CustomDistributedPubSubExtension(context.system).mediator

  mediator ! Subscribe(Topics.ClientFSMReplaced, self)

  def receive = {
    case SubscribeAck(Subscribe(Topics.ClientFSMReplaced, None, `self`)) =>
      context become ready
  }

  def ready = LoggingReceive {
    case CreateClientFSM(sid, name, state, data) =>
      implicit val timeout = Timeout(5.seconds)
      implicit val ec = context.dispatcher
      val originalSender = sender

      manager ? ManagerCreateFSM(name, state, data) onSuccess {
        case actorRef =>
          clients.put(sid, name)
          originalSender ! actorRef
      }
    case GetClientFSM(sid) =>
      sender ! clients.get(sid)
    case Hooks.ClientFSMReplaced(oldName, newName) =>
      if (clients.contains(oldName)) {
        clients.update(oldName, newName)
      }
  }
}