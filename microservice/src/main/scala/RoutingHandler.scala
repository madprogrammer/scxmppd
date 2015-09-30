package main.scala

trait RoutingHandler {
  val name: String
  def handle(original: Route, mangled: Option[Route]): Option[Route]
}
