#!/bin/sh

set -ex

export JVM_ARGS=""

## configures Log4j2
JVM_ARGS="${JVM_ARGS} -Dvertx.disableDnsResolver=true"
## maps vert.x logs to log4j2
# configures netty optimisations (gets rid of the startup stack traces)
JVM_ARGS="${JVM_ARGS} -Dio.netty.tryReflectionSetAccessible=true"
JVM_ARGS="${JVM_ARGS} --add-opens java.base/jdk.internal.misc=ALL-UNNAMED"
# Run in utc
JVM_ARGS="${JVM_ARGS} -Duser.timezone=UTC"

# shellcheck disable=SC2086
exec java ${JVM_ARGS} ${JAVA_OPTIONS} -jar app.jar
