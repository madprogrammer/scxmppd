package com.scxmpp.http

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.codec.http._
import io.netty.util.CharsetUtil
import com.typesafe.config.Config

class HealthCheckWebHandler(config: Config) extends UriBasedHandler(config) {
  def process(ctx: ChannelHandlerContext, request: FullHttpRequest) {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
      Unpooled.copiedBuffer("OK\r\n", CharsetUtil.UTF_8))
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }
}
