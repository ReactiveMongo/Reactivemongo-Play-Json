resolvers ++= Seq(
  Resolver.bintrayIvyRepo("typesafe", "sbt-plugins"),
  "Tatami Releases" at "https://raw.github.com/cchantep/tatami/master/releases")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

addSbtPlugin("cchantep" % "sbt-scaladoc-compiler" % "0.1")

addSbtPlugin("cchantep" % "sbt-hl-compiler" % "0.7")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.1.0")
