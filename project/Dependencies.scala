import sbt._
import sbt.Keys._

object Dependencies {

  object Netty {
    private val version = "4.0.27.Final"
    val all = "io.netty" % "netty-all" % version
    val epoll = "io.netty" % "netty-transport-native-epoll" % version
  }

  private val config = "com.typesafe" % "config" % "1.3.0"
  private val aalto  = "com.fasterxml" % "aalto-xml" % "0.9.11"
  private val akka   = "com.typesafe.akka" %% "akka-actor" % "2.3.13"
  private val icu4j  = "com.ibm.icu" % "icu4j" % "55.1"

  val microservice = dependencies(Netty.all, Netty.epoll, config, aalto, akka, icu4j)

  private def dependencies(modules: ModuleID*): Seq[Setting[_]] = Seq(libraryDependencies ++= modules)
}
