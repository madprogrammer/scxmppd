package main.scala

import com.typesafe.config.{Config, ConfigFactory}
import akka.actor.ReflectiveDynamicAccess

abstract class Context(val config: Config) {
  def this() {
    this {
      Option(System.getenv().get("ENV")) map { e =>
        ConfigFactory.load(s"application.$e")
      } getOrElse {
        ConfigFactory.load()
      }
    }
  }

  val dynamicAccess = new ReflectiveDynamicAccess(getClass.getClassLoader)
}

class ServerContext extends Context {
  val endpoint = new EndpointSettings(config)
  val xmpp = new XmppSettigns(config)
  val ssl = new SslSettings(config)
  val routing = new RoutingSettings(config)
}

class EndpointSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "endpoint")
  val address = config.getString("endpoint.address")
  val port = config.getInt("endpoint.port")
}

class SslSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "ssl")
  val certfile = config.getString("ssl.certfile")
  val keyfile = config.getString("ssl.keyfile")
  val provider = config.getString("ssl.provider")
}

class XmppSettigns(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "xmpp")
  val hosts = config.getStringList("xmpp.hosts")
}

class RoutingSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "routing")
  val pipeline = config.getStringList("routing.pipeline")
  val modules = config.getStringList("routing.modules")
}

