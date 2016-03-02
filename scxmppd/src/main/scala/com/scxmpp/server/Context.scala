package com.scxmpp.server

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

