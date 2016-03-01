package com.scxmpp.server

import java.io.File
import com.typesafe.config.Config
import io.netty.handler.ssl.{SslContext, SslContextBuilder, SslProvider}

object SslContextHelper {
  def getContext(cfg: Config): Option[SslContext] = {
    cfg.hasPath("ssl") match {
      case true =>
        val sslContextBuilder = SslContextBuilder.forServer(
          new File(cfg.getString("ssl.certfile")), new File(cfg.getString("ssl.keyfile")))
        cfg.getString("ssl.provider") match {
          case "jdk" =>
            sslContextBuilder.sslProvider(SslProvider.JDK)
          case "openssl" =>
            sslContextBuilder.sslProvider(SslProvider.OPENSSL)
          case _ =>
            sslContextBuilder.sslProvider(SslProvider.JDK)
        }
        Some(sslContextBuilder.build)
      case false =>
        None
    }
  }
}
