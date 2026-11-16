#!/bin/sh
# Compile the example configurations for both target platforms.
# The results replace the content of example-configurations/output.
set -eu
cd "$(dirname "$0")"

EXAMPLES=example-configurations

./gradlew run --args="-c $EXAMPLES/configurations -o $EXAMPLES/output/teamcity -t teamcity -b false"
./gradlew run --args="-c $EXAMPLES/configurations -o $EXAMPLES/output/gitlab -t gitlab -e $EXAMPLES/environments/GitLabEnv.yaml -b false"
