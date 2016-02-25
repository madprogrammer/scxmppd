package com.scxmpp.util

import java.util.concurrent.ThreadLocalRandom

object RandomUtils {

  def randomDigits(n: Int): String = Seq.fill(n)(ThreadLocalRandom.current.nextInt(0, 10)).flatMap(_.toString).mkString

}
