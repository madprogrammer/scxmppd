package com.scxmpp.http

import com.scxmpp.server.{ServerContext, SslContextHelper}
import com.typesafe.config.Config
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}

class MappedWebServerInitializer(context: ServerContext, config: Config) extends ChannelInitializer[SocketChannel] {
  var sslContext = SslContextHelper.getContext(config)

  override def initChannel(s: SocketChannel): Unit = {
    val p = s.pipeline()
    if (sslContext isDefined)
      p.addLast(sslContext.get.newHandler(s.alloc))
    p.addLast("decoder", new HttpRequestDecoder())
    p.addLast("encoder", new HttpResponseEncoder())
    p.addLast("handler", new MappedWebServerHandler(context, config))
  }
}
