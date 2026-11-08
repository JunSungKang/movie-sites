# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS build
WORKDIR /build
ENV GRADLE_USER_HOME=/build/.cache/gradle
ENV JAVA_TOOL_OPTIONS="-XX:-UsePerfData -Djava.io.tmpdir=/build/.cache/tmp"
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY --chmod=755 gradlew ./
COPY src ./src
RUN --mount=type=cache,target=/build/.cache/gradle mkdir -p .cache/tmp && ./gradlew --no-daemon build

FROM eclipse-temurin:25-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 moviesites && useradd --uid 10001 --gid 10001 --no-create-home moviesites \
    && mkdir -p /app /data/work/tmp && chown -R 10001:10001 /app /data
WORKDIR /app
COPY --from=build /build/build/libs/moviesites.jar /app/moviesites.jar
COPY LICENSE THIRD_PARTY_NOTICES.md /app/
COPY --chmod=755 <<'SCRIPT' /app/start
#!/bin/sh
set -eu
: "${MOVIESITES_MEDIA_ROOT:?Set the external media root}"
: "${MOVIESITES_DATA_ROOT:?Set the external data root}"
mkdir -p "$MOVIESITES_DATA_ROOT/work/tmp"
exec java "-Djava.io.tmpdir=$MOVIESITES_DATA_ROOT/work/tmp" -jar /app/moviesites.jar "$@"
SCRIPT
ENV MOVIESITES_MEDIA_ROOT=/media MOVIESITES_DATA_ROOT=/data PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=35 -XX:InitialRAMPercentage=10 -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData"
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=4s --start-period=30s --retries=3 CMD curl --fail --silent "http://127.0.0.1:${PORT:-8080}/api/health" || exit 1
ENTRYPOINT ["/app/start"]
