import javax.net.ssl.SSLContext
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import akka.actor.ActorSystem
import main.scala.XmlElementEncoder

class ServerInitializer(sslContext: SSLContext, actorSystem: ActorSystem) extends ChannelInitializer[SocketChannel] {
  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    p.addLast("decoder", new XmlFrameDecoder())
    p.addLast("encoder", new XmlElementEncoder());
    p.addLast("handler", new ServerHandler(sslContext, actorSystem))
  }
}
