package com.scxmpp.http

import com.scxmpp.server.ServerContext
import com.typesafe.config.Config
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil

import scala.collection.JavaConversions._
import scala.collection.{immutable, mutable}

class MappedWebServerHandler(context: ServerContext, config: Config) extends SimpleChannelInboundHandler[Object] {
  val buffer = new StringBuilder()
  val handlers = mutable.HashMap.empty[String, UriBasedHandler]

  if (handlers.isEmpty) {
    try {
      for(handler <- config.getConfigList("handlers")) {
        val path = handler.getString("path")
        val clazz = context.dynamicAccess.createInstanceFor[UriBasedHandler](handler.getString("module"), immutable.Seq.empty).get
        handlers += (path -> clazz)
      }
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext) {
    ctx.flush
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: Object) {
    val request = msg match {
      case req: HttpRequest => Some(req)
      case _ => None
    }

    val handler = request match {
      case Some(req) =>
        val path = new QueryStringDecoder(req.getUri).path
        handlers.get(path)
      case _ => None
    }

    if (handler isDefined)
      handler.get.process(msg.asInstanceOf[HttpRequest], buffer)

    msg match {
      case content: LastHttpContent =>
        val response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          if (content.getDecoderResult.isSuccess)
            if (handler isDefined) HttpResponseStatus.OK else HttpResponseStatus.NOT_FOUND
          else HttpResponseStatus.BAD_REQUEST,
          Unpooled.copiedBuffer(buffer.toString(), CharsetUtil.UTF_8))
        response.headers().set("Content-Type", if (handler isDefined) handler.get.getContentType else "text/plain")
        response.headers().set("Connection", HttpHeaders.Values.CLOSE)
        response.headers().set("Content-Length", response.content().readableBytes())
        ctx.write(response)
        ctx.flush
        ctx.close
      case _ =>
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause.printStackTrace()
    ctx.close
  }
}
