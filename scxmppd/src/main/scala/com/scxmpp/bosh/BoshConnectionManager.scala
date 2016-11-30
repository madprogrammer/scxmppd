package com.scxmpp.bosh

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import akka.util.Timeout
import com.scxmpp.c2s.{ClientFSM, CreateClientFSM => ManagerCreateFSM}
import com.typesafe.config.Config

import akka.pattern.ask
import scala.concurrent.duration._

import scala.collection.mutable

case class CreateClientFSM(sid: String, name: String, state: ClientFSM.State, data: ClientFSM.ClientState)
case class GetClientFSM(sid: String)

class BoshConnectionManager(config: Config) extends Actor with ActorLogging {
  val clients = mutable.HashMap.empty[String, String]
  val manager = context.actorSelection("/user/c2s")

  def receive = LoggingReceive {
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
  }
}