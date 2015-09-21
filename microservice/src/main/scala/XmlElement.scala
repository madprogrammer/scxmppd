package main.scala

case class XmlElement(name: String, attrs: List[(String, String)], var body: String, var children: List[XmlElement]) {

  def apply(name: String): Option[String] = {
    XmlElement.attr(name, attrs)
  }

  def child(name: String): Option[XmlElement] = {
    XmlElement.child(name, children)
  }

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

}
