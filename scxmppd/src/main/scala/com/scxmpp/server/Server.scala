package com.scxmpp.server

import java.net.InetSocketAddress

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelOption, EventLoopGroup, ServerChannel, ChannelInitializer}

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import com.typesafe.config.Config

class Server(context: ServerContext) {

  var channels = ArrayBuffer.empty[Channel]
  var bootstraps = ArrayBuffer.empty[ServerBootstrap]

  private def doRun(group: EventLoopGroup, clazz: Class[_ <: ServerChannel]): Unit = {
    try {
      for(server <- context.servers) {
        val bootstrap = new ServerBootstrap()
        bootstrap.childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(true))
        val initializer = context.dynamicAccess.createInstanceFor[ChannelInitializer[Channel]](
          server.getString("module"), List(classOf[ServerContext] -> context, classOf[Config] -> server)).get
        bootstrap.group(group).channel(clazz).childHandler(initializer)

        val inet: InetSocketAddress = new InetSocketAddress(
          server.getString("endpoint.address"), server.getInt("endpoint.port"))
        channels.add(bootstrap.bind(inet).sync.channel)
      }
    } finally {
      for (channel <- channels)
        channel.closeFuture.sync
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
