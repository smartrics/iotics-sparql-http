#!/bin/bash

set -ex

export JVM_ARGS=""

JVM_ARGS="${JVM_ARGS} -Dvertx.disableDnsResolver=true"
# configures netty optimisations (gets rid of the startup stack traces)
JVM_ARGS="${JVM_ARGS} -Dio.netty.tryReflectionSetAccessible=true"
JVM_ARGS="${JVM_ARGS} --add-opens java.base/jdk.internal.misc=ALL-UNNAMED"
# Run in utc
JVM_ARGS="${JVM_ARGS} -Duser.timezone=UTC"

CLASSPATH="app.jar:lib/*"
MAIN=smartrics.iotics.sparqlhttp.SparqlEndpoint

# shellcheck disable=SC2086
exec java -classpath ${CLASSPATH} ${JVM_ARGS} ${JAVA_OPTIONS} ${MAIN}
