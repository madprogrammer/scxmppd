package com.scxmpp.server

import java.net.InetSocketAddress

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.epoll.{Epoll, EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelOption, EventLoopGroup, ServerChannel}

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

class Server(context: ServerContext) {

  var channels = ArrayBuffer.empty[Channel]

  val bootstrap = new ServerBootstrap()
  bootstrap.childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(true))

  private def doRun(group: EventLoopGroup, clazz: Class[_ <: ServerChannel]): Unit = {
    try {
      for(server <- context.servers) {
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
