import sbt.Keys._
import sbt._

object Compiler {
  lazy val settings = Seq(
    scalaVersion := "2.12.4",
    crossScalaVersions := Seq("2.11.11", scalaVersion.value),
    crossVersion in ThisBuild := CrossVersion.binary,
    scalacOptions ++= Seq(
      "-encoding", "UTF-8", "-target:jvm-1.8",
      "-unchecked",
      "-deprecation",
      "-feature",
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-value-discard",
      "-Ywarn-infer-any",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-g:vars"
    ),
    scalacOptions in Compile ++= {
      if (!scalaVersion.value.startsWith("2.11.")) Nil
      else Seq(
        "-Yconst-opt",
        "-Yclosure-elim",
        "-Ydead-code",
        "-Yopt:_"
      )
    },
    scalacOptions in Test ~= {
      _.filterNot(_ == "-Xfatal-warnings")
    },
    scalacOptions in (Compile, doc) := (scalacOptions in Test).value,
    scalacOptions in (Compile, console) ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    scalacOptions in (Test, console) ~= {
      _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
    },
    scalacOptions in (Compile, doc) ++= Seq(
      "-Ywarn-dead-code", "-Ywarn-unused-import", "-unchecked", "-deprecation",
      /*"-diagrams", */"-implicits", "-skip-packages", "samples") ++
      Opts.doc.title("ReactiveMongo Play JSON API") ++
      Opts.doc.version(Release.major.value),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/")
  )
}
