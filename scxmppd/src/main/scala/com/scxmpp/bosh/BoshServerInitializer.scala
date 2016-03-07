package com.scxmpp.bosh

import com.scxmpp.netty.XmlElementDecoder
import com.scxmpp.server.{ServerContext, SslContextHelper}
import com.typesafe.config.Config
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.{HttpServerCodec, HttpObjectAggregator}
import io.netty.handler.stream.ChunkedWriteHandler

class BoshServerInitializer(context: ServerContext, config: Config) extends ChannelInitializer[SocketChannel] {
  var sslContext = SslContextHelper.getContext(config)

  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    if (sslContext.isDefined)
      p.addLast(sslContext.get.newHandler(s.alloc))

    // Decoders
    p.addLast("httpCodec", new HttpServerCodec())
    p.addLast("httpAggregator", new HttpObjectAggregator(65536))
    p.addLast("xmlFrameDecoder", new HttpXmlFrameDecoder())
    p.addLast("xmlElementDecoder", new XmlElementDecoder())

    // Encoders
    p.addLast("xmlHttpResponseEncoder", new XmlHttpResponseEncoder())
    p.addLast("chunked", new ChunkedWriteHandler())

    // Handler
    p.addLast("handler", new BoshXmppServerHandler(context.actorSystem))
  }
}