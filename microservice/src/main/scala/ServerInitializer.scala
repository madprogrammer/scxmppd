import javax.net.ssl.SSLContext
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import akka.actor.ActorSystem

class ServerInitializer(sslContext: SSLContext, actorSystem: ActorSystem) extends ChannelInitializer[SocketChannel] {
  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    p.addLast("decoder", new XmlFrameDecoder())
    p.addLast("handler", new ServerHandler(sslContext, actorSystem))
  }
}
