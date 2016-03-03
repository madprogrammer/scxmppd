package com.scxmpp.http

import com.typesafe.config.Config
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest

abstract class UriBasedHandler(config: Config) {
  def process (ctx: ChannelHandlerContext, request: FullHttpRequest)
}
