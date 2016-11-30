package com.scxmpp.bosh

import java.util

import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.evt.EventAllocatorImpl
import com.fasterxml.aalto.stax.InputFactoryImpl
import com.scxmpp.netty.ResettableChannelInboundHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.http.FullHttpRequest

class HttpXmlFrameDecoder extends MessageToMessageDecoder[FullHttpRequest]
  with ResettableChannelInboundHandler {

  val factory = new InputFactoryImpl()
  var reader = factory.createAsyncForByteBuffer
  val allocator = EventAllocatorImpl.getDefaultInstance

  def reset() {
    reader = factory.createAsyncForByteBuffer
  }

  @throws[Exception]
  protected override def decode(ctx: ChannelHandlerContext, msg: FullHttpRequest, out: util.List[Object]): Unit = {
    val buffer = msg.content()

    reader.getInputFeeder.feedInput(buffer.nioBuffer)
    buffer.skipBytes(buffer.readableBytes)

    while (reader.hasNext && reader.next != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
      out.add(allocator.allocate(reader))
    }

    reset()
  }
}