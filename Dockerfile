ARG ALPINE_VERSION=3.20
ARG OPENJDK_VERSION=21-jdk

FROM maven:3.9.6-eclipse-temurin-21 as builder

RUN mkdir /build

WORKDIR /build

COPY ./pom.xml ./pom.xml
COPY ./lib ./lib
COPY ./src ./src

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

# Install curl
RUN apk add --no-cache curl

FROM alpine:${ALPINE_VERSION} as app-server
ARG VERSION
ENV JAVA_HOME=/opt/java-minimal
ENV \
  httpPort=8080 \
  httpHost=0.0.0.0
  PATH="$PATH:$JAVA_HOME/bin"

WORKDIR /app

COPY --from=packager "$JAVA_HOME" "$JAVA_HOME"
COPY --from=builder /build/target/smartrics.iotics.iotics-sparql-http-SHADED.jar ./app.jar
COPY ./src/main/resources/log4j2.xml ./docker-entrypoint.sh ./

HEALTHCHECK --timeout=2s CMD echo hello | /websocat -q "ws://${httpHost}:${httpPort}" -1E || exit 1

ENTRYPOINT ["./docker-entrypoint.sh"]
