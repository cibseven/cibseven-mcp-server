# syntax=docker/dockerfile:1
#
# The application jar is built on the CI runner (see the workflow, which uses
# setup-java's Maven cache) and only consumed here. The one non-trivial thing
# this image does is ship a jlink-trimmed JRE instead of a full one
# (~180MB -> ~70MB); everything else is a plain copy of the pre-built jar.
# Local build: `mvn package -DskipTests` then `docker build .`.
ARG JAVA=17

# ---- Build a minimal JRE (musl, so it runs on the alpine runtime below) ----
FROM eclipse-temurin:${JAVA}-jdk-alpine AS jre-builder
# Standard Spring Boot module set, kept deliberately broad to cover Tomcat,
# Spring Security and the MCP libraries: jdk.net is required by the HTTP client;
# jdk.charsets / jdk.localedata / jdk.security.auth cover charset, i18n and
# LDAP/JAAS at runtime.
RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.httpserver,jdk.jfr,jdk.localedata,jdk.management,jdk.naming.dns,jdk.naming.rmi,jdk.net,jdk.security.auth,jdk.unsupported,jdk.zipfs \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output /jre

# ---- Runtime ----
FROM alpine:3.23
WORKDIR /app
ENV JAVA_HOME=/opt/jre \
    PATH="/opt/jre/bin:${PATH}" \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

RUN apk add --no-cache ca-certificates libstdc++ tini tzdata \
    && addgroup -g 1000 -S app \
    && adduser -u 1000 -S app -G app -h /app -s /bin/false -D app

COPY --from=jre-builder /jre /opt/jre
# Deliberately specific (not target/*.jar): a stale jar from a previous artifactId
# in an uncleaned target/ must never end up in the image.
COPY --chown=app:app target/cibseven-mcp-server-*.jar app.jar

USER app
EXPOSE 8080
# Kubernetes ignores HEALTHCHECK (the Helm chart defines HTTP probes); this serves
# plain `docker run` users. busybox wget ships with alpine.
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1
# tini as PID 1; exec keeps the JVM as tini's direct child (receives SIGTERM).
ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
