import sbt._
import sbt.Keys._

object Dependencies {

  object Netty {
    private val version = "4.1.0.CR2"
    val all = "io.netty" % "netty-all" % version
    val epoll = "io.netty" % "netty-transport-native-epoll" % version
    val tcnative = "io.netty" % "netty-tcnative" % "1.1.33.Fork7" classifier "linux-x86_64"
  }

  object Akka {
    private val version = "2.3.14"
    val actor = "com.typesafe.akka" %% "akka-actor" % version
    val cluster = "com.typesafe.akka" %% "akka-cluster" % version
    val contrib = "com.typesafe.akka" %% "akka-contrib" % version
  }

  private val config = "com.typesafe" % "config" % "1.2.1"
  private val icu4j  = "com.ibm.icu" % "icu4j" % "55.1"
  private val aalto  = "com.fasterxml" % "aalto-xml" % "0.9.11"
  private val shapeless = "com.chuusai" %% "shapeless" % "2.2.5"
  private val pickling = "org.scala-lang.modules" %% "scala-pickling" % "0.10.1"

  private val etcd4j = "org.mousio" % "etcd4j" % "2.10.0"

  val scxmppd = dependencies(
    Netty.all, Netty.epoll, Netty.tcnative,
    Akka.actor, Akka.cluster, Akka.contrib,
    config, icu4j, aalto, shapeless, etcd4j, pickling)

  private def dependencies(modules: ModuleID*): Seq[Setting[_]] = Seq(libraryDependencies ++= modules)
}
