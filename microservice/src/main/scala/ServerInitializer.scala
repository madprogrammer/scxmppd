package main.scala

import javax.net.ssl.SSLContext
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import akka.actor.ActorSystem

class ServerInitializer(context: MicroserviceContext, sslContext: SSLContext, actorSystem: ActorSystem) extends ChannelInitializer[SocketChannel] {
  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    p.addLast("xmlFrameDecoder", new XmlFrameDecoder())
    p.addLast("xmlEncoder", new XmlElementEncoder())
    p.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8))
    p.addLast("handler", new ServerHandler(context, sslContext, actorSystem))
  }
}
