package main.scala

import java.io.File
import java.net.InetSocketAddress

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelOption, EventLoopGroup, ServerChannel}
import io.netty.handler.ssl.{SslContextBuilder, SslProvider}

import akka.actor.{ActorSystem, Props}

class Server(context: ServerContext) {

  private def doRun(group: EventLoopGroup, clazz: Class[_ <: ServerChannel]): Unit = {
    try {
      val sslContextBuilder = SslContextBuilder.forServer(
        new File(context.ssl.certfile), new File(context.ssl.keyfile))
      context.ssl.provider match {
        case "jdk" =>
          sslContextBuilder.sslProvider(SslProvider.JDK)
        case "openssl" =>
          sslContextBuilder.sslProvider(SslProvider.OPENSSL)
        case _ =>
          sslContextBuilder.sslProvider(SslProvider.JDK)
      }
      val sslContext = sslContextBuilder.build

      val actorSystem = ActorSystem("system")
      actorSystem.actorOf(Props[ClusterListener], "clusterListener")
      actorSystem.actorOf(Props(classOf[Router], context), "router")
      actorSystem.actorOf(Props(classOf[C2SManager], context, actorSystem), "c2s")

      val inet: InetSocketAddress = new InetSocketAddress(
        context.endpoint.address, context.endpoint.port)
      val bootstrap = new ServerBootstrap()
      bootstrap.group(group).channel(clazz).childHandler(new ServerInitializer(context, sslContext, actorSystem))
      bootstrap.childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(true))
      bootstrap.bind(inet).sync.channel.closeFuture.sync
    } finally {
      group.shutdownGracefully.sync
    }
  }

  def run(): Unit = {
    if (Epoll.isAvailable) {
      doRun(new EpollEventLoopGroup, classOf[EpollServerSocketChannel])
    }
    else {
      doRun(new NioEventLoopGroup, classOf[NioServerSocketChannel])
    }
  }
}
