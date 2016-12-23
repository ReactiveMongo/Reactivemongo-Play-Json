import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifacts

organization := "org.reactivemongo"

name := "reactivemongo-play-json"

val nextRelease = "0.12.1"
val buildVersion = nextRelease

version := s"$buildVersion-play24"

scalaVersion in ThisBuild := "2.11.8"

scalacOptions ++= Seq(
  "-unchecked", "-deprecation", "-target:jvm-1.8",
  "-Ywarn-unused-import", "-Ywarn-value-discard", "-Ywarn-dead-code")

scalacOptions in (Compile, doc) ++= Seq(
  "-Ywarn-dead-code", "-Ywarn-unused-import", "-unchecked", "-deprecation",
  /*"-diagrams", */"-implicits", "-skip-packages", "samples") ++
  Opts.doc.title("ReactiveMongo Play JSON API") ++
  Opts.doc.version(nextRelease)

crossScalaVersions := Seq(scalaVersion.value)

crossVersion := CrossVersion.binary

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= {
  val playVer = sys.env.get("PLAY_VERSION").getOrElse("2.4.8")

  Seq(
    "org.reactivemongo" %% "reactivemongo" % buildVersion % "provided" cross CrossVersion.binary,
    "com.typesafe.play" %% "play-json" % playVer % "provided" cross CrossVersion.binary)
}

// Test
fork in Test := false

testOptions in Test += Tests.Cleanup(cl => {
  import scala.language.reflectiveCalls
  val c = cl.loadClass("Common$")
  type M = { def close(): Unit }
  val m: M = c.getField("MODULE$").get(null).asInstanceOf[M]
  m.close()
})

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "3.8.6",
  "org.slf4j" % "slf4j-simple" % "1.7.13").map(_ % Test)

// Travis CI
val travisEnv = taskKey[Unit]("Print Travis CI env")

travisEnv in Test := { // test:travisEnv from SBT CLI
  val specs = List[(String, List[String])](
    "PLAY_VERSION" -> List("2.3.10", "2.5.9")
  )

  def matrix = specs.flatMap {
    case (key, values) => values.map(key -> _)
  }.combinations(specs.size).collect {
    case flags if (flags.map(_._1).toSet.size == specs.size) =>
      flags.sortBy(_._1).map { case (k, v) => s"$k=$v" }
  }.map { c => s"""  - ${c mkString " "}""" }

  println(s"""Travis CI env:\r\n${matrix.mkString("\r\n")}""")
}

// Publish
val previousVersion = "0.12.0"
val mimaSettings = mimaDefaultSettings ++ Seq(
  previousArtifacts := Set(
    organization.value %% moduleName.value % previousVersion)
)

lazy val publishSettings = {
  @inline def env(n: String): String = sys.env.get(n).getOrElse(n)

  val repoName = env("PUBLISH_REPO_NAME")
  val repoUrl = env("PUBLISH_REPO_URL")

  mimaSettings ++ Seq(
    publishMavenStyle := true,
    publishArtifact in Test := false,
    publishTo := Some(repoUrl).map(repoName at _),
    credentials += Credentials(repoName, env("PUBLISH_REPO_ID"),
        env("PUBLISH_USER"), env("PUBLISH_PASS")),
    pomIncludeRepository := { _ => false },
    licenses := {
      Seq("Apache 2.0" ->
        url("http://www.apache.org/licenses/LICENSE-2.0"))
    },
    homepage := Some(url("http://reactivemongo.org")),
    autoAPIMappings := true,
    pomExtra := (
      <scm>
        <url>git://github.com/ReactiveMongo/ReactiveMongo-Play-Json.git</url>
        <connection>scm:git://github.com/ReactiveMongo/ReactiveMongo-Play-Json.git</connection>
      </scm>
      <developers>
        <developer>
          <id>sgodbillon</id>
          <name>Stephane Godbillon</name>
          <url>http://stephane.godbillon.com</url>
        </developer>
      </developers>))
}

// FindBugs
import de.johoop.findbugs4sbt.{ FindBugs, ReportType }, FindBugs.{
  findbugsExcludeFilters, findbugsReportPath, findbugsReportType,
  findbugsSettings
}

findbugsSettings

findbugsExcludeFilters := Some(
  scala.xml.XML.loadFile(baseDirectory.value / "project" / (
    "findbugs-exclude-filters.xml"))
)

findbugsReportType := Some(ReportType.PlainHtml)

findbugsReportPath := Some(target.value / "findbugs.html")

// Scalariform
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value.
  setPreference(AlignParameters, false).
  setPreference(AlignSingleLineCaseStatements, true).
  setPreference(CompactControlReadability, false).
  setPreference(CompactStringConcatenation, false).
  setPreference(DoubleIndentClassDeclaration, true).
  setPreference(FormatXml, true).
  setPreference(IndentLocalDefs, false).
  setPreference(IndentPackageBlocks, true).
  setPreference(IndentSpaces, 2).
  setPreference(MultilineScaladocCommentsStartOnFirstLine, false).
  setPreference(PreserveSpaceBeforeArguments, false).
  setPreference(PreserveDanglingCloseParenthesis, true).
  setPreference(RewriteArrowSymbols, false).
  setPreference(SpaceBeforeColon, false).
  setPreference(SpaceInsideBrackets, false).
  setPreference(SpacesAroundMultiImports, true).
  setPreference(SpacesWithinPatternBinders, true)

lazy val root = (project in file(".")).
  settings(publishSettings: _*)
