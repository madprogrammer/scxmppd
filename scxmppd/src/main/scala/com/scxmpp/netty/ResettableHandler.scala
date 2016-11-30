package com.scxmpp.netty

import io.netty.channel.ChannelInboundHandlerAdapter

trait ResettableChannelInboundHandler extends ChannelInboundHandlerAdapter {
  def reset(): Unit
}
