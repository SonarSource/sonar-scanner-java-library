#!/bin/bash

set -euo pipefail

# FIPS' AMI runs on root, but ES can't run under root. We need to run under ec2-user.
# But the git clone is done by root...
# So the solution was to give ec2-user ownership of the workspace

chown -R ec2-user /tmp/cirrus-ci-build "${GRADLE_USER_HOME}"

# sudo -E because we need to preserve Cirrus' env
# From ShellCheck wiki:
#     $* and ${array[*]} will expand into multiple other arguments: baz, foo, bar, file.txt and otherfile.jpg
#     Since the latter is rarely expected or desired, ShellCheck warns about it.
# This behavior is expected and desired in this case.
# shellcheck disable=SC2048,SC2086
sudo -E -u ec2-user $*

