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
    resolvers += "Local Maven Repository" at (if (System.getProperty("os.name").startsWith("Windows")) "" else "file://") + Path.userHome.absolutePath + "/.m2/repository"
  )

  private lazy val shared = build ++ Testing.settings
  private lazy val runnable = mainClass in Compile := Some("Main")

  lazy val microservice = shared ++ runnable ++ Dependencies.microservice
  lazy val root = build ++ runnable
}
