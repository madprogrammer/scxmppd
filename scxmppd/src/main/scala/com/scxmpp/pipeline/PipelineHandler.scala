package com.scxmpp.pipeline

trait PipelineHandler {
  val name: String
  def handle(original: Route, mangled: Option[Route]): Option[Route]
}
