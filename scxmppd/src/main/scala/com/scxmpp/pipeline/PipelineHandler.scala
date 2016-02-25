package com.scxmpp.pipeline

import com.scxmpp.routing.Route

trait PipelineHandler {
  val name: String
  def handle(original: Route, mangled: Option[Route]): Option[Route]
}
