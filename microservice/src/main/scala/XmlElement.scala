package main.scala

case class XmlElement(name: String, attrs: List[(String, String)], var body: String, var children: List[XmlElement])
