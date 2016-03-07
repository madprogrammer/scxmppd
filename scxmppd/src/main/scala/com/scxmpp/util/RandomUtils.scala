package com.scxmpp.util

import java.util.concurrent.ThreadLocalRandom
import scala.util.Random.javaRandomToRandom

object RandomUtils {

  def randomDigits(n: Int) = Seq.fill(n)(ThreadLocalRandom.current.nextInt(0, 10)).flatMap(_.toString).mkString
  def randomString(n: Int) = ThreadLocalRandom.current().alphanumeric.take(n).mkString
}
