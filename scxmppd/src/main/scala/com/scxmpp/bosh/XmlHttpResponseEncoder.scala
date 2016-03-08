package com.scxmpp.bosh

import java.util
import com.scxmpp.netty.XmlElementEncoder
import com.scxmpp.xml.XmlElement
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpUtil, HttpResponseStatus, HttpVersion, DefaultFullHttpResponse}
import io.netty.util.CharsetUtil

class XmlHttpResponseEncoder extends XmlElementEncoder {

  override def encode(ctx: ChannelHandlerContext, msg: XmlElement, out: util.List[Object]) = {
    val string = elementAsString(msg)
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
      Unpooled.copiedBuffer(string, CharsetUtil.UTF_8))
    HttpUtil.setContentLength(response, string.length)
    response.headers.add("Access-Control-Allow-Origin", "*")
    out.add(response)
  }
}
