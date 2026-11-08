# Docker deployment

[한국어](ko/docker.md) · [Documentation index](../README.md#documentation)

Use Docker with a Linux container engine. The multi-stage Dockerfile builds with JDK 25 and the committed Gradle Wrapper, then runs the executable JAR with a Java runtime and Ubuntu FFmpeg packages. No separate frontend server is required. This repository currently provides a source-build Dockerfile; it does not advertise an unverified prebuilt registry image.

## Local quick start

Use the complete [README command](../README.md#docker) for a first run. Prepare the bind-mount directories before creating the container. Media is mounted at `/media` read-only and generated data at `/data` writable. The sample environment file uses those container paths.

The image's default user is UID/GID `10001:10001`. Give that user write access to the data directory, or use your non-root host UID/GID on Linux/macOS with `--user "$(id -u):$(id -g)"` for user-owned mounts. Do not solve permission errors by making every directory world-writable. Docker Desktop host sharing must also allow the selected paths.

On Windows, use Linux containers and absolute shared Windows host paths, for example `source=C:\Media,target=/media,readonly`. The POSIX `$(pwd)` and `id` commands in the quick start are not PowerShell commands; replace the mount sources and omit that POSIX user expression.

## Persistent container

For a background service, use `-d --restart unless-stopped` instead of the quick start's foreground `--rm` mode. Useful optional limits are `--read-only --memory=2g --cpus=2 --pids-limit=128`; these example limits must be validated against the actual workload. `/data` remains writable, and startup puts Java temporary files below that mount. Keep each instance's data root separate because it is locked exclusively.

Stop and replace the container to change environment settings or application versions. Reuse the external mounts to retain original media and generated data. Keep the prior image/container configuration for rollback until the candidate has passed your checks. Never run old and new instances simultaneously against the same data root.

## NAS and network drives

Mount network storage on the Docker host first, verify that it is available, then bind that host path into `/media`. Keep generated data on suitable writable storage. Java secure directory operations must work on the chosen filesystem/mount. Test a listing, an actual file and a range request; a healthy application endpoint does not certify the entire NAS.

No full-tree scan or recursive watcher is started. A stalled OS filesystem call may outlive an application timeout, so storage failure and resource behavior need environment-specific tests.

## Network access and diagnostics

The README publishes only `127.0.0.1:8080`, which is local to the host. Choose publish addresses, firewall rules, access control and a TLS endpoint deliberately when serving other devices. The app currently has no login; reachable visitors can browse/play the configured media root. `PORT` changes the internal HTTP port, so update the publish mapping too.

Check `/api/health`, inspect container health and read startup logs. Health means the application is responding, not that every media file or NAS connection is valid. For errors and recovery steps see [troubleshooting](troubleshooting.md). For environment variables see [configuration](configuration.md).
