package com.scxmpp.xml

case class XmlElement(name: String, attrs: List[(String, String)], var body: String, var children: List[XmlElement]) {

  def apply(name: String) = XmlElement.attr(name, attrs)

  def child(name: String) = XmlElement.child(name, children)

  def firstChild = XmlElement.firstChild(children)

  def setAttr(name: String, value: String) =
    XmlElement(this.name, (attrs.toMap + (name -> value)).toList, body, children)

  def removeAttr(name: String) =
    XmlElement(this.name, (attrs.toMap - name).toList, body, children)

}

object XmlElement {

  def attr(name: String, attrs: List[(String, String)]): Option[String] = {
    for (attr <- attrs if attr._1 == name)
      return Some(attr._2)
    None
  }

  def child(name: String, children: List[XmlElement]): Option[XmlElement] = {
    for (child <- children if child.name == name)
      return Some(child)
    None
  }

  def firstChild(children: List[XmlElement]): Option[XmlElement] = {
    children.headOption
  }

}
