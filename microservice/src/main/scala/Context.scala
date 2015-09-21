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
  val keystore = new KeystoreSettings(config)
  val xmpp = new XmppSettigns(config)
}

class EndpointSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "endpoint")
  val port = config.getInt("endpoint.port")
}

class KeystoreSettings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "keystore")
  val location = config.getString("keystore.location")
  val password = config.getString("keystore.password")
}

class XmppSettigns(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "xmpp")
  val hosts = config.getStringList("xmpp.hosts")
}

