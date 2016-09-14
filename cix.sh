#!/bin/bash
set -euo pipefail
echo "Running with SQ=$SQ_VERSION JAVA_VERSION=$JAVA_VERSION"

#deploy the version built by travis
CURRENT_VERSION=`mvn help:evaluate -Dexpression="project.version" | grep -v '^\[\|Download\w\+\:'`
RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
NEW_VERSION="$RELEASE_VERSION-build$CI_BUILD_NUMBER"
echo $NEW_VERSION

mvn versions:set -DnewVersion=$NEW_VERSION
cd its
mvn verify -Prun-its -Dsonar.runtimeVersion=$SQ_VERSION -DjavaVersion=$JAVA_VERSION
