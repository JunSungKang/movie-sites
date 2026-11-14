# syntax=docker/dockerfile:1
ARG JAVA_BUILD_IMAGE=moviesites-java-build:25-ubuntu26.04
ARG JAVA_RUNTIME_IMAGE=moviesites-java-runtime:25-ubuntu26.04
FROM ${JAVA_BUILD_IMAGE} AS build
USER root
RUN . /etc/os-release && test "$ID" = ubuntu && test "$VERSION_ID" = 26.04 \
    && javac -version 2>&1 | grep -E '^javac 25([. ]|$)'
WORKDIR /build
ENV GRADLE_USER_HOME=/build/.cache/gradle
ENV JAVA_TOOL_OPTIONS="-XX:-UsePerfData -Djava.io.tmpdir=/build/.cache/tmp"
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY --chmod=755 gradlew ./
COPY src ./src
RUN --mount=type=cache,target=/build/.cache/gradle mkdir -p .cache/tmp && ./gradlew --no-daemon build

FROM ${JAVA_RUNTIME_IMAGE} AS runtime
USER root
# Java's native path/environment decoding follows the OS locale, not file.encoding.
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
RUN . /etc/os-release && test "$ID" = ubuntu && test "$VERSION_ID" = 26.04 \
    && java -version 2>&1 | grep -E 'Temurin-25([.+-]|$)'
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
