import com.typesafe.tools.mima.core._, ProblemFilters._
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings

organization := "org.reactivemongo"

name := "reactivemongo-play-json"

version ~= { ver =>
  sys.env.get("RELEASE_SUFFIX") match {
    case Some(suffix) => ver.span(_ != '-') match {
      case (a, b) => s"${a}-${suffix}${b}"
    }
    case _ => ver
  }
}

Compiler.settings

import Compiler.{ playLower, playUpper }

val playVer = Def.setting[String] {
  sys.env.get("PLAY_VERSION").getOrElse {
    if (scalaVersion.value startsWith "2.11.") playLower
    else playUpper
  }
}

val playDir = Def.setting[String] {
  if (playVer.value startsWith "2.5") "play-upto2.5"
  else "play-2.6+"
}

unmanagedSourceDirectories in Compile += {
  (sourceDirectory in Compile).value / playDir.value
}

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe repository releases".at(
    "http://repo.typesafe.com/typesafe/releases/"),
  "Tatami Snapshots".at(
    "https://raw.github.com/cchantep/tatami/master/snapshots")
)

libraryDependencies ++= {
  val silencerVer = "1.4.2"

  def silencer = Seq(
    compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVer),
    "com.github.ghik" %% "silencer-lib" % silencerVer % Provided)

  Seq(
  "org.reactivemongo" %% "reactivemongo" % (version in ThisBuild).value % Provided cross CrossVersion.binary,
    "com.typesafe.play" %% "play-json" % playVer.value % Provided cross CrossVersion.binary) ++ silencer
}

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
  "org.specs2" %% "specs2-core" % "4.7.0",
  "org.slf4j" % "slf4j-simple" % "1.7.28").map(_ % Test)

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
  mimaFailOnNoPrevious := false,
  mimaPreviousArtifacts := {
    if (!scalaVersion.value.startsWith("2.13.")) {
      Set(organization.value %% moduleName.value % previousVersion)
    } else {
      Set.empty[ModuleID]
    }
  },
  mimaBinaryIssueFilters ++= {
    def playFilters = {
      // ValidationError breaking change in Play 2.6
      (Seq("ObjectID", "Binary", "String", "Symbol", "MaxKey", "Undefined",
        "Long", "Array", "Null", "MinKey", "DateTime", "Integer", "Double",
        "Timestamp", "Regex", "Document", "Boolean", "JavaScript", "Decimal").
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
      // Deprecated BatchCommands
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$DeleteElementWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$LastErrorReader$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$UpdateReader$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$InsertWriter$"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONBatchCommands.DeleteWriter"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONBatchCommands.UpdateWriter"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONBatchCommands.CountWriter"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONBatchCommands.UpdateReader"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONBatchCommands.CountResultReader"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONBatchCommands.InsertWriter"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$WriteConcernWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$UpdateWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$HintWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$UpsertedReader$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$CountWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$DeleteWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$DefaultWriteResultReader$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$UpdateElementWriter$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$CountResultReader$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$WriteConcernErrorReader$"),
      ProblemFilters.exclude[MissingClassProblem]("reactivemongo.play.json.collection.JSONBatchCommands$WriteErrorReader$"),
      // ---
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.JSONSerializationPack.IdentityReader"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.JSONSerializationPack.IdentityWriter"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.JSONSerializationPack.serializeAndWrite"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.JSONSerializationPack.readAndDeserialize"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.JSONSerializationPack.readAndDeserialize"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONBatchCommands.DistinctResultReader"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONBatchCommands.DistinctWriter"),
      // ---
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.package.jsWriter"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.BSONFormats.jsWriter"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.ImplicitBSONHandlers.jsWriter"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.package.jsWriter"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.elementProducer"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.commands.JSONFindAndModifyCommand.UpdateLastError"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.commands.JSONFindAndModifyCommand.UpdateLastError"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.collection.JSONCollection.distinct$default$2"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.collection.JSONCollection.distinct"),
      ProblemFilters.exclude[IncompatibleSignatureProblem]("reactivemongo.play.json.collection.JSONBatchCommands#DistinctResultReader.reads"),
      // ---
      ProblemFilters.exclude[ReversedMissingMethodProblem]("reactivemongo.play.json.BSONFormats.reactivemongo$play$json$BSONFormats$$defaultRead"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("reactivemongo.play.json.BSONFormats.reactivemongo$play$json$BSONFormats$_setter_$reactivemongo$play$json$BSONFormats$$defaultWrite_="),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("reactivemongo.play.json.BSONFormats.reactivemongo$play$json$BSONFormats$_setter_$reactivemongo$play$json$BSONFormats$$defaultRead_="),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("reactivemongo.play.json.BSONFormats.reactivemongo$play$json$BSONFormats$$defaultWrite"),
      ProblemFilters.exclude[ReversedMissingMethodProblem]("reactivemongo.play.json.BSONFormats.BSONDecimalFormat"),
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
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONBatchCommands.LastErrorReader"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.Mongo26WriteCommand"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.emptyCapped"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate$default$3"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate$default$4"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate$default$5"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate$default$6"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1$default$3"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1$default$4"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1$default$5"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1$default$6"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1$default$7"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1$default$8"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.aggregateWith"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.bulkInsert"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.bulkInsert$default$3"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.runCommand"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.runValueCommand"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.runWithResponse"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.save"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.save$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.sister"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.sister$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.sister$default$3"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.uncheckedInsert"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.uncheckedRemove"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.uncheckedRemove$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.uncheckedUpdate"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.uncheckedUpdate$default$3"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONCollection.uncheckedUpdate$default$4"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.cursor"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.merge"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.collect$default$1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.collect$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerate"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerate$default$1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerate$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerateBulks"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerateBulks$default$1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerateBulks$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerateResponses"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerateResponses$default$1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.enumerateResponses$default$2"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.rawEnumerateResponses"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.rawEnumerateResponses$default$1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.toList"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.toList$default$1"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.toList$default$2"),
      ProblemFilters.exclude[FinalMethodProblem]("reactivemongo.play.json.collection.JSONCollection.fullCollectionName"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("reactivemongo.play.json.collection.JSONCollection.aggregate1"),
      ProblemFilters.exclude[IncompatibleMethTypeProblem]("reactivemongo.play.json.collection.JsCursorImpl.collect"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.merge"),
      ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.PipelineOperator"),
      ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("reactivemongo.play.json.BSONFormats#PartialReads.reactivemongo$play$json$BSONFormats$PartialReads$$$outer"),
      ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("reactivemongo.play.json.BSONFormats#PartialWrites.reactivemongo$play$json$BSONFormats$PartialWrites$$$outer"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONBatchCommands.DefaultWriteResultReader")
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


lazy val root = (project in file(".")).
  settings(publishSettings ++ Release.settings)
