#!/usr/bin/env bash
#
# TEMPORARY - remove once Bouncy Castle 1.86 is released to Maven Central.
#
# Seeds the vendored Bouncy Castle 1.86 beta into the local Maven repository so
# that lib/pom.xml can depend on it like any other artifact.
#
# Why not a file:// repository in the POM? Both the developer and the CI Maven
# settings mirror every repository to Repox (<mirrorOf>*</mirrorOf>), which also
# intercepts file:// repositories declared in a POM. Pre-populating ~/.m2 is the
# only placement the mirror cannot redirect.
#
# Why plain shell rather than "mvn install:install-file"? In the CI build job
# nothing has configured Maven yet at the point this runs, so resolving the
# install plugin would itself hit the network.
#
# Usage: seed-bc-beta.sh [path-to-jar]
#
set -euo pipefail

readonly GROUP_PATH="org/bouncycastle"
readonly ARTIFACT_ID="bcprov-jdk18on"
readonly VERSION="1.86-beta"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../.." && pwd)"
source_jar="${1:-${repository_root}/lib/beta-deps/${ARTIFACT_ID}-${VERSION}.jar}"

if [[ ! -f "${source_jar}" ]]; then
  echo "Bouncy Castle beta jar not found: ${source_jar}" >&2
  exit 1
fi

target_directory="${HOME}/.m2/repository/${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}"
mkdir -p "${target_directory}"
cp "${source_jar}" "${target_directory}/${ARTIFACT_ID}-${VERSION}.jar"

# Drop any cached "could not be resolved" markers from an earlier attempt.
rm -f "${target_directory}"/*.lastUpdated

# bcprov has no dependencies of its own, so a minimal POM is enough.
cat > "${target_directory}/${ARTIFACT_ID}-${VERSION}.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.bouncycastle</groupId>
  <artifactId>${ARTIFACT_ID}</artifactId>
  <version>${VERSION}</version>
  <packaging>jar</packaging>
  <name>Bouncy Castle Provider (1.86 beta)</name>
</project>
EOF

# Marks both files as locally installed, so the resolver never asks a remote for them.
cat > "${target_directory}/_remote.repositories" <<EOF
${ARTIFACT_ID}-${VERSION}.jar>=
${ARTIFACT_ID}-${VERSION}.pom>=
EOF

echo "Seeded ${ARTIFACT_ID}:${VERSION} into ${target_directory}"
