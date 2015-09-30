package main.scala

import scala.util.{Success, Failure}
import scala.concurrent.duration._
import scala.collection.immutable
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.LoggingReceive
import akka.actor._
import akka.util.Timeout
import java.util.logging.Logger

case class Route(from: JID, to: JID, element: XmlElement)

class Router(serverContext: MicroserviceContext) extends Actor with ActorLogging {
  import CustomDistributedPubSubMediator.SendToAll
  val mediator = CustomDistributedPubSubExtension(context.system).mediator
  val logger = Logger.getLogger(getClass.getName)
  val pipeline = constructPipeline

  def constructPipeline: immutable.ListMap[String, RoutingHandler] = {
    immutable.ListMap((for (
      name <- serverContext.routing.handlers.toList;
      clazz = serverContext.dynamicAccess.createInstanceFor[RoutingHandler](name, immutable.Seq.empty).get
    ) yield (clazz.name -> clazz)): _*)
  }

  def receive = LoggingReceive {
    case route @ Route(from, to, element) =>
      pipeline.values.foldLeft[Option[Route]](Some(route)) { case (acc, handler) => handler.handle(route, acc) } match {
        case Some(result) =>
          mediator ! SendToAll("/user/c2s/%s".format(to.toActorPath), result, false)
        case None =>
          logger.info("Message %s discarded after pipeline processing".format(route))
      }
    }
}
