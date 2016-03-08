package com.scxmpp.netty

import java.util
import javax.xml.stream._
import java.io.StringWriter
import java.nio.CharBuffer
import java.nio.charset.Charset
import com.scxmpp.xml.XmlElement
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder

class XmlElementEncoder extends MessageToMessageEncoder[XmlElement] {

  val outputFactory = XMLOutputFactory.newInstance
  val eventFactory = XMLEventFactory.newInstance

  private def writeElement(el: XmlElement, eventWriter: XMLEventWriter) {
    eventWriter.add(eventFactory.createStartElement("", "", el.name))
    for (attr <- el.attrs)
      eventWriter.add(eventFactory.createAttribute(attr._1, attr._2))
    if (el.body.length > 0 || el.name == "stream:stream")
      eventWriter.add(eventFactory.createCharacters(el.body))
    for (child <- el.children)
      writeElement(child, eventWriter)
    if (el.name != "stream:stream")
      eventWriter.add(eventFactory.createEndElement("", "", el.name))
  }

  protected def elementAsString(element: XmlElement) = {
    val stringWriter = new StringWriter
    val eventWriter = outputFactory.createXMLEventWriter(stringWriter)

    writeElement(element, eventWriter)
    eventWriter.flush()
    val result = stringWriter.toString
    eventWriter.close()

    result
  }

  @throws[Exception]
  override def encode(ctx: ChannelHandlerContext, message: XmlElement, out: util.List[Object]) {
    out.add(ByteBufUtil.encodeString(ctx.alloc, CharBuffer.wrap(elementAsString(message)), Charset.defaultCharset))
  }

}
