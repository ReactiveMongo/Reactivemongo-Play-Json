#! /usr/bin/env bash

set -e

FS="build.sbt project/plugins.sbt"

for F in $FS; do
    grep -vi 'scapegoat' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
done

rm -f project/Scapegoat.scala
