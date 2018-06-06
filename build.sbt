import com.typesafe.tools.mima.core._, ProblemFilters._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.{
  mimaBinaryIssueFilters,
  mimaPreviousArtifacts
}

organization := "org.reactivemongo"

name := "reactivemongo-play-json"

scalaVersion in ThisBuild := "2.12.6"

version ~= { ver =>
  sys.env.get("RELEASE_SUFFIX") match {
    case Some(suffix) => ver.span(_ != '-') match {
      case (a, b) => s"${a}-${suffix}${b}"
    }
    case _ => ver
  }
}

crossScalaVersions in ThisBuild := Seq("2.11.12", scalaVersion.value)

crossVersion in ThisBuild := CrossVersion.binary

scalacOptions ++= Seq(
  "-encoding", "UTF-8", "-target:jvm-1.8",
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-Xfatal-warnings",
  "-Xlint",
  "-Ywarn-numeric-widen",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard",
  "-Ywarn-infer-any",
  "-Ywarn-unused",
  "-Ywarn-unused-import",
  "-g:vars"
)

scalacOptions in Compile ++= {
  if (!scalaVersion.value.startsWith("2.11.")) Nil
  else Seq(
    "-Yconst-opt",
    "-Yclosure-elim",
    "-Ydead-code",
    "-Yopt:_"
  )
}

scalacOptions in Test ~= {
  _.filterNot(_ == "-Xfatal-warnings")
}

scalacOptions in (Compile, doc) := (scalacOptions in Test).value

scalacOptions in (Compile, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

scalacOptions in (Test, console) ~= {
  _.filterNot { opt => opt.startsWith("-X") || opt.startsWith("-Y") }
}

scalacOptions in (Compile, doc) ++= Seq(
  "-Ywarn-dead-code", "-Ywarn-unused-import", "-unchecked", "-deprecation",
  /*"-diagrams", */"-implicits", "-skip-packages", "samples") ++
  Opts.doc.title("ReactiveMongo Play JSON API") ++
  Opts.doc.version(Release.major.value)

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository releases" at "http://repo.typesafe.com/typesafe/releases/")

val playLower = "2.5.0"
val playUpper = "2.6.2"

val playVer = Def.setting[String] {
  sys.env.get("PLAY_VERSION").getOrElse {
    if (scalaVersion.value startsWith "2.11.") playLower
    else playUpper
  }
}

val playDir = Def.setting[String] {
  if (playVer.value startsWith "2.6") "play-2.6"
  else "play-upto2.5"
}

unmanagedSourceDirectories in Compile += {
  (sourceDirectory in Compile).value / playDir.value
}

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo" % (version in ThisBuild).value % Provided cross CrossVersion.binary,
  "com.typesafe.play" %% "play-json" % playVer.value % Provided cross CrossVersion.binary)

// Test
unmanagedSourceDirectories in Test += {
  (sourceDirectory in Test).value / playDir.value
}

fork in Test := false

testOptions in Test += Tests.Cleanup(cl => {
  import scala.language.reflectiveCalls
  val c = cl.loadClass("Common$")
  type M = { def close(): Unit }
  val m: M = c.getField("MODULE$").get(null).asInstanceOf[M]
  m.close()
})

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "4.2.0",
  "org.slf4j" % "slf4j-simple" % "1.7.13").map(_ % Test)

// Travis CI
val travisEnv = taskKey[Unit]("Print Travis CI env")

travisEnv in Test := { // test:travisEnv from SBT CLI
  val specs = List[(String, List[String])](
    "PLAY_VERSION" -> List(playLower, playUpper)
  )

  lazy val integrationEnv = specs.flatMap {
    case (key, values) => values.map(key -> _)
  }.combinations(specs.size).toList

  @inline def integrationVars(flags: List[(String, String)]): String =
    flags.map { case (k, v) => s"$k=$v" }.mkString(" ")

  def integrationMatrix =
    integrationEnv.map(integrationVars).map { c => s"  - $c" }

  def matrix = (("env:" +: integrationMatrix :+
    "matrix: " :+ "  exclude: ") ++ (
    integrationEnv.flatMap { flags =>
      if (/* time-compat exclusions: */
        flags.contains("PLAY_VERSION" -> playUpper)) {
        List(
          "    - scala: 2.11.12",
          s"      env: ${integrationVars(flags)}"
        )
      } else if (/* time-compat exclusions: */
        flags.contains("PLAY_VERSION" -> playLower)) {
        List(
          "    - scala: ${scalaVersion.value}",
          s"      env: ${integrationVars(flags)}"
        )
      } else List.empty[String]
    })
  ).mkString("\r\n")

  println(s"# Travis CI env\r\n$matrix")
}

// Publish
val previousVersion = "0.12.1"
val mimaSettings = mimaDefaultSettings ++ Seq(
  mimaPreviousArtifacts := Set(
    organization.value %% moduleName.value % previousVersion),
  mimaBinaryIssueFilters ++= {
    def playFilters = {
      // ValidationError breaking change in Play 2.6
      (Seq("ObjectID", "Binary", "String", "Symbol", "MaxKey", "Undefined",
        "Long", "Array", "Null", "MinKey", "DateTime", "Integer", "Double",
        "Timestamp", "Regex", "Document", "Boolean", "JavaScript").
        flatMap { t =>
          Seq("filter", "filterNot", "collect").map { m =>
            ProblemFilters.exclude[IncompatibleMethTypeProblem](
              s"reactivemongo.play.json.BSONFormats#BSON${t}Format.${m}")
          }
        }) ++ (Seq("LastError", "Update", "Upserted", "DefaultWriteResult",
          "CountResult", "WriteConcernError", "WriteError", "DistinctResult"
        ).flatMap { t =>
          Seq("filter", "filterNot", "collect").map { m =>
            ProblemFilters.exclude[IncompatibleMethTypeProblem](s"reactivemongo.play.json.collection.JSONBatchCommands#${t}Reader.${m}")
          }
        }) ++ (Seq(
          "LowerImplicitBSONHandlers#BSONValueReads",
          "JSONSerializationPack#IdentityReader",
          "commands.JSONFindAndModifyImplicits#FindAndModifyResultReader",
          "commands.CommonImplicits#UnitBoxReader",
          "commands.JSONAggregationImplicits#AggregationResultReader"
        ).flatMap { t =>
          Seq("filter", "filterNot", "collect").map { m =>
            ProblemFilters.exclude[IncompatibleMethTypeProblem](
              s"reactivemongo.play.json.${t}.${m}")
          }
        })
    }

    playFilters ++ (Seq("Writes", "Reads").map { m =>
      ProblemFilters.exclude[InheritedNewAbstractMethodProblem](s"reactivemongo.play.json.BSONFormats#PartialFormat.reactivemongo$$play$$json$$BSONFormats$$Partial${m}$$$$$$outer")
    }) ++ Seq(
      ProblemFilters.exclude[DirectAbstractMethodProblem]("play.api.libs.json.Reads.reads"),
      ProblemFilters.exclude[MissingClassProblem](
        "reactivemongo.play.json.BSONFormats$BSONTimestampFormat$TimeValue$"),
      ProblemFilters.exclude[MissingClassProblem](
        "reactivemongo.play.json.BSONFormats$BSONDateTimeFormat$DateValue$"),
      ProblemFilters.exclude[MissingClassProblem](
        "reactivemongo.play.json.BSONFormats$BSONObjectIDFormat$OidValue$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.BSONFormats$BSONJavaScriptFormat$JavascriptValue$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.BSONFormats$BSONSymbolFormat$SymbolValue$"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "reactivemongo.play.json.BSONFormats.readAsBSONValue"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "reactivemongo.play.json.BSONFormats.writeAsJsValue"),
      ProblemFilters.exclude[UpdateForwarderBodyProblem](
        "reactivemongo.play.json.BSONFormats#PartialFormat.reads"),
      ProblemFilters.exclude[UpdateForwarderBodyProblem](
        "reactivemongo.play.json.BSONFormats#PartialFormat.writes"),
      ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("reactivemongo.play.json.BSONFormats#PartialWrites.reactivemongo$play$json$BSONFormats$PartialWrites$$$outer"),
      ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("reactivemongo.play.json.BSONFormats#PartialReads.reactivemongo$play$json$BSONFormats$PartialReads$$$outer"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.PipelineOperator"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.sister"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.sister$default$2"),
      ProblemFilters.exclude[FinalMethodProblem]("reactivemongo.play.json.collection.JSONCollection.fullCollectionName"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.sister$default$3"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.merge"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.cursor"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.cursor"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONBatchCommands.LastErrorReader")
    )
  }
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
findbugsExcludeFilters := Some(
  scala.xml.XML.loadFile(baseDirectory.value / "project" / (
    "findbugs-exclude-filters.xml"))
)

findbugsReportType := Some(FindbugsReport.PlainHtml)

findbugsReportPath := Some(target.value / "findbugs.html")

// Scalariform
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

ScalariformKeys.preferences := ScalariformKeys.preferences.value.
  setPreference(AlignParameters, false).
  setPreference(AlignSingleLineCaseStatements, true).
  setPreference(CompactControlReadability, false).
  setPreference(CompactStringConcatenation, false).
  setPreference(DoubleIndentConstructorArguments, true).
  setPreference(FormatXml, true).
  setPreference(IndentLocalDefs, false).
  setPreference(IndentPackageBlocks, true).
  setPreference(IndentSpaces, 2).
  setPreference(MultilineScaladocCommentsStartOnFirstLine, false).
  setPreference(PreserveSpaceBeforeArguments, false).
  setPreference(DanglingCloseParenthesis, Preserve).
  setPreference(RewriteArrowSymbols, false).
  setPreference(SpaceBeforeColon, false).
  setPreference(SpaceInsideBrackets, false).
  setPreference(SpacesAroundMultiImports, true).
  setPreference(SpacesWithinPatternBinders, true)

//Scapegoat.settings

lazy val root = (project in file(".")).
  settings(publishSettings ++ Release.settings)
