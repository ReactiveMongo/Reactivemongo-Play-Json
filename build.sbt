import Common.{ playVersion, driverVersion }

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
        "org.reactivemongo" %% "reactivemongo-bson-api" % driverVersion.value,
        playJson.value).
        map { _ % Provided cross CrossVersion.binary }
    }))

lazy val root = (project in file(".")).
  settings(Release.settings ++ Seq(
    publish := ({}),
    publishTo := None,
    mimaPreviousArtifacts := Set.empty[ModuleID], // TODO
  )).
  aggregate(`play-json-compat`)
