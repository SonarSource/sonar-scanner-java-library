#!/bin/bash

set -euo pipefail

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v57 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
if [[ -n ${TRAVIS_SECURE_ENV_VARS+x} && ${TRAVIS_SECURE_ENV_VARS} == true ]]; then
  configureTravis

  export DEPLOY_PULL_REQUEST=true

  regular_mvn_build_deploy_analyze
else
  echo "Travis secure variables not available"
  mvn install
fi
