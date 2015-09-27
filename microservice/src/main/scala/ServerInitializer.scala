package main.scala

import io.netty.handler.ssl.SslContext
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import akka.actor.ActorRef

class ServerInitializer(context: MicroserviceContext, sslContext: SslContext, manager: ActorRef) extends ChannelInitializer[SocketChannel] {
  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    p.addLast(sslContext.newHandler(s.alloc))
    p.addLast("xmlFrameDecoder", new XmlFrameDecoder())
    p.addLast("xmlEncoder", new XmlElementEncoder())
    p.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8))
    p.addLast("handler", new ServerHandler(context, manager))
  }
}
