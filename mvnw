#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
mkdir -p "$BASE/.cache/tmp" "$BASE/.cache/maven" "$BASE/.cache/m2"
MAVEN_USER_HOME="$BASE/.cache/maven"
export MAVEN_USER_HOME
exec java -XX:-UsePerfData "-Djava.io.tmpdir=$BASE/.cache/tmp" "-Dmaven.user.home=$MAVEN_USER_HOME" "-Dmaven.multiModuleProjectDirectory=$BASE" -classpath "$BASE/.mvn/wrapper/maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain "-Dmaven.repo.local=$BASE/.cache/m2" "$@"
