#!/bin/bash
set -euo pipefail
echo "Running with SQ=$SQ_VERSION JAVA_VERSION=$JAVA_VERSION"

cd its
mvn verify -Prun-its -Dsonar.runtimeVersion=$SQ_VERSION -DjavaVersion=$JAVA_VERSION
