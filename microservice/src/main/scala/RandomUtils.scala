package main.scala

import java.util.Base64
import java.util.concurrent.ThreadLocalRandom

import scala.util.Random.javaRandomToRandom

object RandomUtils {

  def randomBytes(n: Int):Array[Byte] = {
    val bytes = new Array[Byte](n)
    ThreadLocalRandom.current().nextBytes(bytes)
    bytes
  }

  def randomString(n: Int): String = ThreadLocalRandom.current().alphanumeric.take(n).mkString
  def randomDigits(n: Int): String = ThreadLocalRandom.current().ints(n, 0, 10).toArray.flatMap(_.toString).mkString
  def randomToken(n: Int): String = Base64.getUrlEncoder.withoutPadding().encodeToString(randomBytes(n))

}
