package main.scala

import com.typesafe.config.{Config, ConfigFactory}

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
}

class MicroserviceContext extends Context {
  val endpoint = new EndpointSettings(config)
  val xmpp = new XmppSettigns(config)
  val ssl = new SslSettings(config)
}

class EndpointSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "endpoint")
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

