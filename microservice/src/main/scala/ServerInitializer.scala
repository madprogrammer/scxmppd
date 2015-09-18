import javax.net.ssl.SSLContext
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelInitializer, ChannelPipeline}

class ServerInitializer(sslContext: SSLContext) extends ChannelInitializer[SocketChannel] {
  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    p.addLast("decoder", new XmlFrameDecoder())
    p.addLast("handler", new ServerHandler(sslContext))
  }
}
