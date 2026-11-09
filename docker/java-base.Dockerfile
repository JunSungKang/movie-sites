# syntax=docker/dockerfile:1
# Project-owned fallback bases. Shared image tags are never changed.
ARG TEMURIN_BUILD_SOURCE=eclipse-temurin:25-jdk-resolute@sha256:422bf3f42dc7f967f36eb4196f6d7b683c415cb541583571833d865c807c2ea2
ARG TEMURIN_RUNTIME_SOURCE=eclipse-temurin:25-jre-resolute@sha256:b6e00330337143a2c1a20f1c1c693d4b6ce9a3c30ade8b9d03fc68cc9e3871e7
FROM ${TEMURIN_BUILD_SOURCE} AS java-build
RUN . /etc/os-release && test "$ID" = ubuntu && test "$VERSION_ID" = 26.04 \
    && java -version 2>&1 | grep -E 'Temurin-25([.+-]|$)'
LABEL io.moviesites.base="ubuntu26.04-temurin25-build"
FROM ${TEMURIN_RUNTIME_SOURCE} AS java-runtime
RUN . /etc/os-release && test "$ID" = ubuntu && test "$VERSION_ID" = 26.04 \
    && java -version 2>&1 | grep -E 'Temurin-25([.+-]|$)'
LABEL io.moviesites.base="ubuntu26.04-temurin25-runtime"
