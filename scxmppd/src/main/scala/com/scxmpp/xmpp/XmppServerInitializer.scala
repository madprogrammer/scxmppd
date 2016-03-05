package com.scxmpp.xmpp

import akka.actor.{ActorSystem, Props}
import com.scxmpp.c2s.C2SManager
import com.scxmpp.cluster.ClusterListener
import com.scxmpp.modules.support.{ModuleManager, HandlerManager}
import com.scxmpp.netty.{XmlElementEncoder, XmlFrameDecoder}
import com.scxmpp.routing.Router
import com.scxmpp.server.{ServerContext, SslContextHelper}
import com.typesafe.config.Config
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil

class XmppServerInitializer(context: ServerContext, config: Config) extends ChannelInitializer[SocketChannel] {
  val actorSystem = ActorSystem("system")
  actorSystem.actorOf(Props[ClusterListener], "clusterListener")
  actorSystem.actorOf(Props(classOf[Router], context, config), "router")
  actorSystem.actorOf(Props(classOf[C2SManager], config), "c2s")
  actorSystem.actorOf(Props(classOf[ModuleManager], context, config), "module")
  actorSystem.actorOf(Props(classOf[HandlerManager], context, config), "handler")

  var sslContext = SslContextHelper.getContext(config)

  override def initChannel(s: SocketChannel): Unit = {
    val p: ChannelPipeline = s.pipeline
    if (sslContext isDefined)
      p.addLast(sslContext.get.newHandler(s.alloc))
    p.addLast("xmlFrameDecoder", new XmlFrameDecoder())
    p.addLast("xmlEncoder", new XmlElementEncoder())
    p.addLast("stringEncoder", new StringEncoder(CharsetUtil.UTF_8))
    p.addLast("handler", new XmppServerHandler(actorSystem))
  }
}
