package com.scxmpp.netty

import java.util
import java.util.logging.Logger

import com.scxmpp.xml.XmlElement
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageDecoder
import javax.xml.stream.events._

import scala.collection.JavaConversions._
import scala.collection.mutable

class XmlElementDecoder extends MessageToMessageDecoder[XMLEvent] {
  val logger = Logger.getLogger(getClass.getName)
  var nodes: mutable.Stack[XmlElement] = mutable.Stack()
  var depth = 0

  def getAttributeTuple(attr: Attribute) = {
    (attr.getName.getPrefix match {
      case prefix if prefix.length > 0 =>
        prefix + ":" + attr.getName.getLocalPart
      case prefix =>
        attr.getName.getLocalPart
    }, attr.getValue)
  }

  def getXmlElement(e: StartElement): XmlElement = {
    val ns = e.getName.getNamespaceURI
    XmlElement(
      e.getName.getLocalPart,
      ns match {
        case uri if uri.length > 0 =>
          "xmlns" -> ns :: e.getAttributes.map(x => getAttributeTuple(x.asInstanceOf[Attribute])).toList
        case uri =>
          e.getAttributes.map(x => getAttributeTuple(x.asInstanceOf[Attribute])).toList
      },
      "", List())
  }

  def reset() {
    depth = 0
    nodes.clear()
  }

  override def decode(ctx: ChannelHandlerContext, event: XMLEvent, out: util.List[Object]) {
    event match {
      case e: StartDocument =>
      case e: StartElement if depth == 0 =>
        depth += 1
        out.add(getXmlElement(e))
      case e: StartElement if depth >= 1 =>
        val element = getXmlElement(e)
        if (nodes.nonEmpty) {
          val parent = nodes.head
          parent.children = element :: parent.children
        }
        nodes.push(element)
        depth += 1
      case e: EndElement =>
        depth -= 1
        if (nodes.nonEmpty) {
          val element = nodes.pop()
          if (depth == 1) {
            out.add(element)
          }
        }
      case e: Characters =>
        if (!e.isWhiteSpace) {
          nodes.head.body = e.getData
        }
      case e: EndDocument =>
      case _ =>
        logger.warning("Got unsupported event: " + event.getClass.getName)
    }
  }
}
