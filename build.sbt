import Common.playVersion

version ~= { ver =>
  sys.env.get("RELEASE_SUFFIX") match {
    case Some(suffix) => ver.span(_ != '-') match {
      case (a, b) => s"${a}-${suffix}${b}"
    }

    case _ => ver
  }
}

// FindBugs
findbugsExcludeFilters := Some(
  scala.xml.XML.loadFile(baseDirectory.value / "project" / (
    "findbugs-exclude-filters.xml"))
)

findbugsReportType := Some(FindbugsReport.PlainHtml)

findbugsReportPath := Some(target.value / "findbugs.html")

lazy val playJson = Def.setting {
  "com.typesafe.play" %% "play-json" % playVersion.value
}

lazy val `play-json-compat` = project.in(file("compat")).
  settings(Seq(
    name := "reactivemongo-play-json-compat",
    description := "Compatibility library between BSON/Play JSON",
    mimaPreviousArtifacts := Set.empty[ModuleID], // TODO
    libraryDependencies ++= {
      val baseVer = (version in ThisBuild).value // w-o play qualifier

      ("org.slf4j" % "slf4j-api" % "1.7.30" % Provided) +: Seq(
        "org.reactivemongo" %% "reactivemongo-bson-api" % baseVer,
        playJson.value).
        map { _ % Provided cross CrossVersion.binary }
    }))

lazy val legacy = project.in(file("legacy")).
  settings(Seq(
    name := "reactivemongo-play-json",
    fork in Test := false,
    libraryDependencies ++= {
      val baseVer = (version in ThisBuild).value // w-o play qualifier

      Seq(
        playJson.value,
        "org.reactivemongo" %% "reactivemongo" % baseVer % Provided cross CrossVersion.binary,
        "org.reactivemongo" %% "reactivemongo-bson-macros" % baseVer % Test)
    },
    testOptions in Test += Tests.Cleanup(cl => {
      import scala.language.reflectiveCalls
      val c = cl.loadClass("Common$")
      type M = { def close(): Unit }
      val m: M = c.getField("MODULE$").get(null).asInstanceOf[M]
      m.close()
    }),
    scalacOptions += "-P:silencer:globalFilters=.*reactivemongo\\.play\\.json\\.compat.*;.*JSONException.*",
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._, ProblemFilters._

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
        ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JsCursorImpl.collect"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.collection.JSONQueryBuilder.merge"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.PipelineOperator"),
        ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("reactivemongo.play.json.BSONFormats#PartialReads.reactivemongo$play$json$BSONFormats$PartialReads$$$outer"),
        ProblemFilters.exclude[InheritedNewAbstractMethodProblem]("reactivemongo.play.json.BSONFormats#PartialWrites.reactivemongo$play$json$BSONFormats$PartialWrites$$$outer"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("reactivemongo.play.json.collection.JSONBatchCommands.DefaultWriteResultReader"),
        // ---
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.TextScore"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.Ascending"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.Descending"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.MetadataSort"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.MetadataSort"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.Descending"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.Ascending"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("reactivemongo.play.json.commands.JSONAggregationFramework.TextScore"),
      )
    }
  ))

lazy val root = (project in file(".")).
  settings(Release.settings ++ Seq(
    mimaPreviousArtifacts := Set.empty[ModuleID], // TODO
  )).
  aggregate(`play-json-compat`, legacy)
