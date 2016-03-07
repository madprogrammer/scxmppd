package com.scxmpp.bosh

import com.scxmpp.netty.XmlElementDecoder
import com.scxmpp.server.{ServerContext, SslContextHelper}
import com.scxmpp.xmpp.XmppServerHandler
import com.typesafe.config.Config
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.{HttpResponseEncoder, HttpObjectAggregator, HttpRequestDecoder}

class BoshServerInitializer(context: ServerContext, config: Config) extends ChannelInitializer[SocketChannel] {
  var sslContext = SslContextHelper.getContext(config)

  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    if (sslContext.isDefined)
      p.addLast(sslContext.get.newHandler(s.alloc))

    // HTTP protocol layer
    p.addLast("httpDecoder", new HttpRequestDecoder())
    p.addLast("httpAggregator", new HttpObjectAggregator(65536))
    p.addLast("httpEncoder", new HttpResponseEncoder())

    // XML frame layer
    p.addLast("xmlFrameDecoder", new HttpXmlFrameDecoder())
    p.addLast("xmlElementDecoder", new XmlElementDecoder())
    p.addLast("handler", new XmppServerHandler(context.actorSystem))
  }
}