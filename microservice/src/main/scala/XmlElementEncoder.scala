package main.scala

import javax.xml.stream._
import java.util.List
import java.io.StringWriter
import java.nio.CharBuffer
import java.nio.charset.Charset
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder

class XmlElementEncoder extends MessageToMessageEncoder[XmlElement] {

  val outputFactory = XMLOutputFactory.newInstance
  val eventFactory = XMLEventFactory.newInstance

  def writeElement(el: XmlElement, eventWriter: XMLEventWriter) {
    eventWriter.add(eventFactory.createStartElement("", "", el.name))
    for (attr <- el.attrs)
      eventWriter.add(eventFactory.createAttribute(attr._1, attr._2))
    if (el.body.length > 0)
      eventWriter.add(eventFactory.createCharacters(el.body))
    for (child <- el.children)
      writeElement(child, eventWriter)
    eventWriter.add(eventFactory.createEndElement("", "", el.name))
  }

  @throws[Exception]
  override def encode(ctx: ChannelHandlerContext, message: XmlElement, out: List[Object]) {
    val stringWriter = new StringWriter
    val eventWriter = outputFactory.createXMLEventWriter(stringWriter)

    writeElement(message, eventWriter)
    eventWriter.close

    out.add(ByteBufUtil.encodeString(ctx.alloc, CharBuffer.wrap(stringWriter.toString), Charset.defaultCharset))
  }

}
