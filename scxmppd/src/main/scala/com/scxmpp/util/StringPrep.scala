package com.scxmpp.util

import com.ibm.icu.text.{StringPrep => IcuPrep}

object StringPrep {
  private val nodePrep = IcuPrep.getInstance(IcuPrep.RFC3920_NODEPREP)
  private val namePrep = IcuPrep.getInstance(IcuPrep.RFC3491_NAMEPREP)
  private val resourcePrep = IcuPrep.getInstance(IcuPrep.RFC3920_RESOURCEPREP)

  def nodePrep(node: String): String = nodePrep.prepare(node, IcuPrep.DEFAULT).toLowerCase
  def namePrep(name: String): String = namePrep.prepare(name, IcuPrep.DEFAULT).toLowerCase
  def resourcePrep(resource: String): String = resourcePrep.prepare(resource, IcuPrep.DEFAULT)
}
