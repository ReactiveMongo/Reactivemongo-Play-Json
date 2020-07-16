#! /usr/bin/env bash

set -e

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

cd "$SCRIPT_DIR/.."

sbt ++$SCALA_VERSION scalariformFormat test:scalariformFormat
git diff --exit-code || (
  echo "ERROR: Scalariform check failed, see differences above."
  echo "To fix, format your sources using ./build scalariformFormat test:scalariformFormat before submitting a pull request."
  echo "Additionally, please squash your commits (eg, use git commit --amend) if you're going to update this pull request."
  false
)

TEST_OPTS="exclude mongo2"

TEST_CMD=";error ;test:compile ;mimaReportBinaryIssues"

if [ "v$SCALA_VERSION" = "v2.12.11" ]; then
  TEST_CMD="$TEST_CMD ;scapegoat"
fi

TEST_CMD="$TEST_CMD ;info ;testQuick * -- $TEST_OPTS"

sbt ++$SCALA_VERSION "$TEST_CMD"
