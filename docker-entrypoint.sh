#!/bin/sh

set -ex

export JVM_ARGS=""

## configures JUL
JVM_ARGS="${JVM_ARGS} -Djava.util.logging.config.file=./logging.properties"
## configures Log4j2
JVM_ARGS="${JVM_ARGS} -Dlog4j.configurationFile=./log4j2.xml"
JVM_ARGS="${JVM_ARGS} -Dvertx.disableDnsResolver=true"
## maps vert.x logs to log4j2
JVM_ARGS="${JVM_ARGS} -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory"
## redirects JUL to log4j2 - doesn't work / requires more work
#JVM_ARGS="${JVM_ARGS} -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"
# configures netty optimisations (gets rid of the startup stack traces)
JVM_ARGS="${JVM_ARGS} -Dio.netty.tryReflectionSetAccessible=true"
JVM_ARGS="${JVM_ARGS} --add-opens java.base/jdk.internal.misc=ALL-UNNAMED"
# Run in utc
JVM_ARGS="${JVM_ARGS} -Duser.timezone=UTC"

if [ "$1" = "local" ]; then
    JVM_ARGS="${JVM_ARGS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

# shellcheck disable=SC2086
exec java ${JVM_ARGS} ${JAVA_OPTIONS} -jar app.jar
