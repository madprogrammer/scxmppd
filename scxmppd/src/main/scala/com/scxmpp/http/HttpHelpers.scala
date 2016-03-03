package com.scxmpp.http

import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.codec.http.{HttpVersion, DefaultFullHttpResponse, HttpResponseStatus}
import io.netty.util.CharsetUtil

object HttpHelpers {

  def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus) {
    val response = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8))
    response.headers().set("Content-Type", "text/plain; charset=UTF-8")

    // Close the connection as soon as the error message is sent.
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

}
