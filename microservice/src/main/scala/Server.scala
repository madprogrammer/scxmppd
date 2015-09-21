package main.scala

import java.io.FileInputStream
import java.net.InetSocketAddress
import java.security.{KeyStore, SecureRandom}

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{ChannelOption, EventLoopGroup, ServerChannel}

import javax.net.ssl.{SSLContext, KeyManagerFactory}

import akka.actor.ActorSystem

class Server(context: MicroserviceContext) {

  private def doRun(group: EventLoopGroup, clazz: Class[_ <: ServerChannel]): Unit = {
    try {
      val keystore = KeyStore.getInstance("JKS")
      keystore.load(new FileInputStream(context.keystore.location),
        context.keystore.password.toCharArray)

      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      kmf.init(keystore, context.keystore.password.toCharArray)

      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(kmf.getKeyManagers, null, new SecureRandom())

      val actorSystem = ActorSystem("FSM")

      val inet: InetSocketAddress = new InetSocketAddress(context.endpoint.port)
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
