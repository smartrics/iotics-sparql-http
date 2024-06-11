ARG UBUNTU_VERSION=20.04

# ------------------------ Build Stage for Java Application ------------------------
FROM maven:3.9.6-eclipse-temurin-21 as builder

WORKDIR /build

COPY ./pom.xml ./pom.xml
COPY ./lib ./lib
COPY ./src ./src

# Build the Java application
RUN mvn clean package -DskipTests

# ------------------------ Build Stage for Go Project ------------------------
FROM golang:1.19-bullseye as gobuilder
ARG GO_REPO_URL=https://github.com/Iotic-Labs/iotics-identity-go.git
WORKDIR /go/src

# Install necessary packages for building Go project and CA certificates
RUN apt-get update && apt-get install -y --no-install-recommends git make gcc libc6-dev

# Clone the Go repository
RUN git clone ${GO_REPO_URL} .

ENV GOOS=linux
ENV GOARCH=amd64

WORKDIR /go/src/ffi

# Build the Go project
RUN go build -buildmode=c-shared -o lib/lib-iotics-id-sdk.so ./ffi_wrapper.go

# ------------------------ Packager Stage ------------------------
FROM ubuntu:${UBUNTU_VERSION} as packager
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jdk-headless binutils curl ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
ENV JAVA_MINIMAL="/opt/java-minimal"

# Build minimal JRE
RUN $JAVA_HOME/bin/jlink \
    --verbose \
    --add-modules \
      java.base,jdk.unsupported,java.logging,java.sql,java.desktop,java.scripting,jdk.crypto.cryptoki,java.management,java.naming \
    --compress 2 --strip-debug --no-header-files --no-man-pages \
    --release-info="add:IMPLEMENTOR=iotic:IMPLEMENTOR_VERSION=iotic_JRE" \
    --output "$JAVA_MINIMAL"

# ------------------------ App Server Stage ------------------------
FROM ubuntu:${UBUNTU_VERSION} as app-server
ENV JAVA_HOME=/opt/java-minimal
ENV PATH="$PATH:$JAVA_HOME/bin"
ENV httpPort=8080
ENV httpHost=0.0.0.0

WORKDIR /app

# Copy curl and ca-certificates from packager stage
COPY --from=packager /usr/bin/curl /usr/bin/curl
COPY --from=packager /etc/ssl/certs /etc/ssl/certs
COPY --from=packager /etc/ca-certificates /etc/ca-certificates
COPY --from=packager /usr/share/ca-certificates /usr/share/ca-certificates
COPY --from=packager /etc/ca-certificates.conf /etc/ca-certificates.conf
COPY --from=packager /var/lib/dpkg/info/ca-certificates* /var/lib/dpkg/info/
# Copy the minimal JRE from the packager stage
COPY --from=packager "$JAVA_HOME" "$JAVA_HOME"
# Copy the built Java application JAR
COPY --from=builder /build/target/smartrics.iotics.iotics-sparql-http-SHADED.jar ./app.jar
# Copy the built .so file from the Go build stage
COPY --from=gobuilder /go/src/ffi/lib/lib-iotics-id-sdk.so ./lib/
# Copy the entrypoint script
COPY ./docker-entrypoint.sh ./

# Ensure the entrypoint script is executable
RUN chmod +x ./docker-entrypoint.sh

# Set the java.library.path to include the directory with the .so file
ENV LD_LIBRARY_PATH=/app/lib

# Health check to ensure the service is running
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 CMD curl -f http://${httpHost}:${httpPort}/health || exit 1

# Set entrypoint and default command
ENTRYPOINT ["./docker-entrypoint.sh"]
