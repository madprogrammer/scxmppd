package com.scxmpp.http

import com.scxmpp.server.{ServerContext, SslContextHelper}
import com.typesafe.config.Config
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.stream.ChunkedWriteHandler

class MappedWebServerInitializer(context: ServerContext, config: Config) extends ChannelInitializer[SocketChannel] {
  var sslContext = SslContextHelper.getContext(config)

  override def initChannel(s: SocketChannel): Unit = {
    val p = s.pipeline()
    if (sslContext isDefined)
      p.addLast(sslContext.get.newHandler(s.alloc))
    p.addLast("codec", new HttpServerCodec())
    p.addLast("aggregator", new HttpObjectAggregator(65536))
    p.addLast("chunked", new ChunkedWriteHandler())
    p.addLast("handler", new MappedWebServerHandler(context, config))
  }
}
