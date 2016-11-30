package com.scxmpp.bosh

import akka.actor.Props
import com.scxmpp.netty.XmlElementDecoder
import com.scxmpp.server.{ServerContext, SslContextHelper}
import com.typesafe.config.Config
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedWriteHandler

class BoshServerInitializer(context: ServerContext, config: Config) extends ChannelInitializer[SocketChannel] {
  var sslContext = SslContextHelper.getContext(config)
  context.actorSystem.actorOf(Props(classOf[BoshConnectionManager], config), "bosh")

  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    if (sslContext.isDefined)
      p.addLast(sslContext.get.newHandler(s.alloc))

    // Decoders
    p.addLast("httpDecoder", new HttpRequestDecoder())
    p.addLast("httpEncoder", new HttpResponseEncoder())

    p.addLast("httpAggregator", new HttpObjectAggregator(65536))
    p.addLast("xmlFrameDecoder", new HttpXmlFrameDecoder())
    p.addLast("xmlElementDecoder", new XmlElementDecoder(false))

    // Encoders
    p.addLast("xmlHttpResponseEncoder", new XmlHttpResponseEncoder())
    p.addLast("chunked", new ChunkedWriteHandler())

    // Handler
    p.addLast("handler", new BoshXmppServerHandler(context))
  }
}