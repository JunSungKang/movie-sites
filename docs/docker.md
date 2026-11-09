# Docker deployment

[한국어](ko/docker.md) · [Documentation index](../README.md#documentation)

The application runs in one Ubuntu 26.04 / Temurin 25 container with FFmpeg, ffprobe and CA certificates. Python and Node are verification tools, not application runtimes. No database or separate API container is required.

## Build images

```bash
docker buildx bake -f docker-bake.hcl --load fallback-bases
docker buildx bake -f docker-bake.hcl --load movie-kids
```

The fallback targets create **project-owned** `movie-kids-java-build:25-ubuntu26.04` and `movie-kids-java-runtime:25-ubuntu26.04`, from digest-pinned official Temurin Ubuntu 26.04 images. They never retag shared images. The application target builds `movie-kids:ubuntu26.04`. Set `JAVA_BUILD_IMAGE` and `JAVA_RUNTIME_IMAGE` to compatible shared references when available; Dockerfile assertions reject the wrong OS/Java major. The build uses the committed Gradle Wrapper. No Node build stage is needed for the static frontend.

FFmpeg, curl and certificates are installed in the project application image, leaving Java bases reusable. The image default remains `10001:10001`; the investigated Compose deployment uses `501:20`. Set the Compose UID/GID to match the existing host ownership. Do not recursively chown original media or grant world-write permissions.

## Compose

Use Docker Compose 2.30+ (raw environment-file support) and Buildx/Bake:

```bash
cp platform.env.example .env.platform
# Edit .env.platform for the host paths, UID/GID, bind address and image.
# Prepare existing, correctly owned movie/ and runtime/ directories first.
docker compose --env-file .env.platform -f compose.platform.yaml config
docker compose --env-file .env.platform -f compose.platform.yaml up -d --no-build --no-deps movie-kids-web
```

The project is `platform`, service `movie-kids-web`, container normally `platform-movie-kids-web-1`. Only **20010:8080** is published by default, bound to loopback. UI and API share that port; web reservations 20011–20014 and API reservations 20110–20114 are not automatically exposed. Set the bind address deliberately for other devices. `MOVIESITES_ENV_FILE` is a separate raw application environment file; it preserves JSON labels and literal dollar signs. `/media`, `/data` and internal port 8080 are fixed in the Compose service.

| Compose input | Default / purpose |
|---|---|
| `JAVA_BUILD_IMAGE`, `JAVA_RUNTIME_IMAGE` | Project fallback bases, overridable at build time |
| `MOVIESITES_IMAGE` | `movie-kids:ubuntu26.04`; use the verified immutable image ID for deployment |
| `MOVIESITES_UID`, `MOVIESITES_GID` | `501`, `20`; preserve the existing numeric owner |
| `MOVIESITES_BIND_ADDRESS`, `MOVIESITES_WEB_PORT` | `127.0.0.1`, `20010` |
| `MOVIESITES_MEDIA_PATH` | `./movie` → `/media`, read-only |
| `MOVIESITES_DATA_PATH` | `./runtime` → `/data`, writable, exclusively locked |
| `MOVIESITES_ENV_FILE` | `.env.example`; copy/edit a private application file to preserve actual settings |

Bind sources must already exist (`create_host_path: false`). No anonymous/named data volume replaces the original directories. Keep one writer per data root; scaling this service against the same `/data` is unsupported. Memory 2 GiB, CPUs 2, PID limit 128, init, read-only root and local logging reproduce the investigated run settings. The 30-second stop grace period accommodates Spring's 15-second graceful shutdown window.

A Compose-managed bridge is used without adding cross-project dependencies. Never `down` the shared `platform` project to update only this service. Use service-scoped `stop`, `rm`, `start` or `up`; never `down -v` or global prune.

## Isolated verification and operating migration

With Python 3.14 and Node 24 for repository checks:

```bash
python3 -B scripts/verify-platform.py --image movie-kids:ubuntu26.04
```

This generates synthetic media and a disposable data root under `.codex-work/platform-release/`, with a unique QA Compose project and only a reserved loopback web port. It tests direct/converted playback, thumbnails, navigation, FFmpeg, traversal/symlink rejection, private stream tokens, read-only roots/media, exclusive data locking, graceful stop and restart. `--keep` retains that QA instance for browser inspection; its `qa-state.json` records the exact isolated Compose command for cleanup. Production data is never mounted by this verification script.

The production migration tool requires a passing report for the exact candidate image and Compose hash, acquires a real advisory deployment lock, preserves the old container/configuration and rolls back on failure. Read the [migration/rollback guide](platform-migration.md) before invoking it. Test actual NAS secure-directory support and outages separately; a healthy HTTP endpoint is not a full storage audit.

## Exclusive data ownership

The original Java advisory FileLock is retained. Actual Docker Desktop verification showed that advisory locks alone can be independent across bind mounts. Storage therefore also atomically creates `/data/.moviesites.owner` before starting any cache/work handling. A second instance fails startup without deleting the first owner's marker. Normal shutdown removes the marker/directory and releases the advisory lock.

After SIGKILL, host failure or an unclean exit, the ownership directory can remain. Startup deliberately fails closed rather than guessing that a slow writer is dead. Stop **every** container/process using that data root, hold the shared deployment lock, inspect the owner evidence, and only then remove that stale directory before restarting one instance. Never clear it while a writer might still be running. Do not use a TTL to steal ownership.
