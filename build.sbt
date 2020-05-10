import Common.{ playVersion, driverVersion }

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
        "org.reactivemongo" %% "reactivemongo-bson-api" % driverVersion.value changing(),
        playJson.value).
        map { _ % Provided cross CrossVersion.binary }
    },
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-matcher-extra" % "4.9.4" % Test)))

lazy val root = (project in file(".")).
  settings(Release.settings ++ Seq(
    publish := ({}),
    publishTo := None,
    mimaPreviousArtifacts := Set.empty[ModuleID], // TODO
  )).
  aggregate(`play-json-compat`)
