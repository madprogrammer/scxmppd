package com.scxmpp.http

import com.typesafe.config.Config
import com.scxmpp.server.ServerContext
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

import scala.collection.JavaConversions._
import scala.collection.{immutable, mutable}

class MappedWebServerHandler(context: ServerContext, config: Config) extends SimpleChannelInboundHandler[FullHttpRequest] {
  val handlers = mutable.HashMap.empty[String, UriBasedHandler]

  if (handlers.isEmpty) {
    try {
      for(handler <- config.getConfigList("handlers")) {
        val path = handler.getString("path")
        val clazz = context.dynamicAccess.createInstanceFor[UriBasedHandler](
          handler.getString("module"), immutable.Seq((classOf[Config], handler))).get
        handlers += (path -> clazz)
      }
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext) {
    ctx.flush
  }

  override def channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val path = new QueryStringDecoder(request.uri).path()
    val key = path.substring(0, path.indexOf("/", 1) match { case -1 => path.length; case x => x })
    val handler = handlers.get(key)

    // Pass the untouched request to the handler for maximum flexibility
    if (handler.isDefined)
      handler.get.process(ctx, request)
    else
      HttpHelpers.sendError(ctx, HttpResponseStatus.NOT_FOUND)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause.printStackTrace()
    if (ctx.channel.isActive)
      HttpHelpers.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR)
  }
}
