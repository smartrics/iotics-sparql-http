ARG ALPINE_VERSION=3.20
ARG OPENJDK_VERSION=21-jdk

FROM maven:3.9.6-eclipse-temurin-21 as builder

RUN mkdir /com.iotic.web.eventing

WORKDIR /com.iotic.web.eventing

COPY ./pom.xml ./pom.xml

COPY ./pb_include ./pb_include/
COPY ./src/main/java ./src/main/java
COPY ./src/main/proto ./src/main/proto
COPY ./src/test/java ./src/test/java

RUN mvn clean package -DskipTests

ENTRYPOINT ["mvn", "clean", "package", "-DskipTests"]

# ------------------------------------------------------------------------------

FROM alpine:${ALPINE_VERSION} as packager
RUN apk --no-cache add openjdk21-jdk openjdk21-jmods binutils
ENV JAVA_MINIMAL="/opt/java-minimal"
# build minimal JRE

RUN /usr/bin/jlink \
    --verbose \
    --add-modules \
      java.base,jdk.unsupported,java.logging,java.sql,java.desktop,java.scripting,jdk.crypto.cryptoki,java.management,java.naming \
    --compress 2 --strip-debug --no-header-files --no-man-pages \
    --release-info="add:IMPLEMENTOR=iotic:IMPLEMENTOR_VERSION=iotic_JRE" \
    --output "$JAVA_MINIMAL"

# Note: Alpine v3.15+ has websocat available in its repos
RUN \
  apk --no-cache add curl && \
  curl -L -o /websocat https://github.com/vi/websocat/releases/download/v1.11.0/websocat.x86_64-unknown-linux-musl && \
  chmod a+x /websocat

# ------------------------------------------------------------------------------

FROM alpine:${ALPINE_VERSION} as stomp-server
ARG VERSION
ARG WEB_API_TAG
ARG WEB_API_STOMP_TOPIC_PREFIX_PATTERN
ENV JAVA_HOME=/opt/java-minimal
ENV \
  httpPort=8080 \
  httpHost=0.0.0.0 \
  wsPath=/ws \
  webApiVersion=$WEB_API_TAG \
  topicPrefixPattern=$WEB_API_STOMP_TOPIC_PREFIX_PATTERN \
  implVersion=$VERSION \
  PATH="$PATH:$JAVA_HOME/bin" \
  LOG_LEVEL=INFO \
  JUL_IO_GRPC_LEVEL=WARNING


WORKDIR /app

COPY --from=packager "$JAVA_HOME" "$JAVA_HOME"
COPY --from=packager /websocat /
COPY --from=builder /com.iotic.web.eventing/target/com.iotic.web.eventing-jar-with-dependencies.jar ./app.jar
COPY ./src/main/resources/logging.properties ./src/main/resources/log4j2.xml ./docker-entrypoint.sh ./
COPY ./conf ./conf/

HEALTHCHECK --timeout=2s CMD echo hello | /websocat -q "ws://${httpHost}:${httpPort}${wsPath}" -1E || exit 1

ENTRYPOINT ["./docker-entrypoint.sh"]
