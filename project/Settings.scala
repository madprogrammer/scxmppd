import sbt._
import sbt.Keys._
import scala.collection.immutable.Seq
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.SbtNativePackager._

object Settings {

  private lazy val build = Seq(
    version := "1.0",
    scalaVersion := "2.11.7",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL + ".m2/repository"
  )

  private lazy val shared = build ++ Testing.settings
  private lazy val runnable = mainClass in Compile := Some("Main")

  lazy val scxmppd = shared ++ runnable ++ Dependencies.scxmppd
  lazy val root = build ++ runnable
}
