package main.scala

import java.util.logging.Logger

class LoggingHandler extends PipelineHandler {
  override val name = "loggingHandler"
  val logger = Logger.getLogger(name)

  def handle(original: Route, mangled: Option[Route]): Option[Route] = {
    logger.info("Route from: %s, to: %s:, mangled: %s"
      .format(mangled.get.from, mangled.get.to, mangled.get.element))
    mangled
  }
}
