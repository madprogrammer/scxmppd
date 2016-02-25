package com.scxmpp.pipeline

import java.util.logging.Logger

import com.scxmpp.routing.Route

class LoggingHandler extends PipelineHandler {
  override val name = "loggingHandler"
  val logger = Logger.getLogger(name)

  def handle(original: Route, mangled: Option[Route]): Option[Route] = {
    logger.info("Route from: %s, to: %s:, mangled: %s"
      .format(mangled.get.from, mangled.get.to, mangled.get.element))
    mangled
  }
}
