package main.scala

import com.ibm.icu.text.{StringPrep => IcuPrep}

object StringPrep {
  private val nodePrep = IcuPrep.getInstance(IcuPrep.RFC3920_NODEPREP)
  private val namePrep = IcuPrep.getInstance(IcuPrep.RFC3491_NAMEPREP)
  private val resourcePrep = IcuPrep.getInstance(IcuPrep.RFC3920_RESOURCEPREP)

  def nodePrep(node: String): String = nodePrep.prepare(node, IcuPrep.DEFAULT)
  def namePrep(name: String): String = namePrep.prepare(name, IcuPrep.DEFAULT)
  def resourcePrep(resource: String): String = resourcePrep.prepare(resource, IcuPrep.DEFAULT)
}
