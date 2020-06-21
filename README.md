# ReactiveMongo Support for Play JSON

This is a JSON serialization pack for [ReactiveMongo](http://reactivemongo.org), based on the JSON library of Play Framework.

## Usage

In your `build.sbt`:

```ocaml
libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo-play-json-compat" % VERSION)
```

[![Maven](https://img.shields.io/maven-central/v/org.reactivemongo/reactivemongo-play-json_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Creactivemongo-play-json) [![Javadocs](https://javadoc.io/badge/org.reactivemongo/reactivemongo-play-json_2.12.svg)](https://javadoc.io/doc/org.reactivemongo/reactivemongo-play-json_2.12)

The documentation is [available online](http://reactivemongo.org/releases/0.1x/documentation/json/overview.html).

> More [examples](src/test/scala/JSONCollectionSpec.scala)

## Build manually

ReactiveMongo for Play Framework can be built from this source repository.

    sbt publishLocal

To run the tests, use:

    sbt test

[![CircleCI](https://circleci.com/gh/ReactiveMongo/ReactiveMongo-Play-Json.svg?style=svg)](https://circleci.com/gh/ReactiveMongo/ReactiveMongo-Play-Json)
[![Test coverage](https://img.shields.io/badge/coverage-69%25-green.svg)](https://reactivemongo.github.io/ReactiveMongo-Play-Json/coverage/0.12.0/)

> As for [Play Framework](http://playframework.com/) 2.5+, a JDK 1.8+ is required to build this plugin.

### Learn More

- [Complete documentation and tutorials](http://reactivemongo.org/releases/0.1x/documentation/tutorial/play2.html)
- [Search or create issues](https://github.com/ReactiveMongo/ReactiveMongo-Play-Json/issues)
- [Get help](https://groups.google.com/forum/?fromgroups#!forum/reactivemongo)
- [Contribute](https://github.com/ReactiveMongo/ReactiveMongo/blob/master/CONTRIBUTING.md#reactivemongo-developer--contributor-guidelines)
