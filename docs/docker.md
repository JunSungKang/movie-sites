# Docker deployment

[한국어](ko/docker.md) · [Documentation index](../README.md#documentation)

MovieSites runs in one Ubuntu 26.04 / Temurin 25 container with FFmpeg, ffprobe and CA certificates. The static frontend, JSON API and video streams share port 8080. Python and Node are development/test tools, not application runtimes.

## Build images

Use Docker with a Linux container engine and Buildx/Bake:

```bash
docker buildx bake -f docker-bake.hcl --load fallback-bases
docker buildx bake -f docker-bake.hcl --load moviesites
```

The fallback targets create project-owned `moviesites-java-build:25-ubuntu26.04` and `moviesites-java-runtime:25-ubuntu26.04` from pinned official Java images. The application target builds `moviesites:ubuntu26.04`. Compatible Java bases can be supplied through `JAVA_BUILD_IMAGE` and `JAVA_RUNTIME_IMAGE`; the Dockerfile checks the OS and Java major version. Shared images are not retagged. The committed Gradle Wrapper builds the application, and only the JAR and runtime components enter the final image.

## External storage and permissions

Original media and generated data must remain outside the image:

| Host path | Container path | Access |
|---|---|---|
| Existing media directory or mounted NAS | `/media` | Read-only |
| Separate generated-data directory | `/data` | Read/write, one active instance |

The image and Compose example default to non-root user `10001:10001`. Set `MOVIESITES_UID` and `MOVIESITES_GID` to an appropriate non-root numeric user/group when the host directories require it. Prepare a writable data directory for that identity and readable original media. Do not recursively change original-media ownership or grant world-write permissions to fix a deployment error. Windows/macOS Docker Desktop users must also allow access to the chosen host paths.

Bind sources must already exist; the example uses `create_host_path: false`. Container environment variables refer to `/media` and `/data`, not the host source paths. Media/data roots must not overlap. Videos, sidecars, thumbnails and prepared playback files never become JAR/image content.

The image and Compose service set `LANG=C.UTF-8` and `LC_ALL=C.UTF-8`. Preserve a UTF-8 native locale for non-ASCII file names and environment values. Java's `file.encoding` alone does not establish native path decoding; inspect `native.encoding` and `sun.jnu.encoding` when adapting the image.

## Compose example

Use Docker Compose 2.30 or later for raw environment-file support:

```bash
cp compose.env.example .env.compose
# Edit the private file for your image, existing paths and numeric user/group.
docker compose --env-file .env.compose -f compose.yaml config
docker compose --env-file .env.compose -f compose.yaml up -d --no-build moviesites
```

The canonical example has one service, `moviesites`, and publishes `127.0.0.1:8080` to the application's port 8080. Change the host port if it is already in use. Use a deliberate bind address and access policy when serving another device. `MOVIESITES_ENV_FILE` names the separate raw application environment file, preserving JSON labels and literal dollar signs. See [configuration](configuration.md) for application settings.

| Compose input | Default / purpose |
|---|---|
| `JAVA_BUILD_IMAGE`, `JAVA_RUNTIME_IMAGE` | Compatible build/runtime bases; project-owned fallback images by default |
| `MOVIESITES_IMAGE` | `moviesites:ubuntu26.04`; use a tested immutable reference when upgrading |
| `MOVIESITES_UID`, `MOVIESITES_GID` | `10001`, `10001`; adapt to non-root host access |
| `MOVIESITES_BIND_ADDRESS`, `MOVIESITES_WEB_PORT` | `127.0.0.1`, `8080` |
| `MOVIESITES_MEDIA_PATH` | `./movie` → `/media`, read-only |
| `MOVIESITES_DATA_PATH` | `./runtime` → `/data`, writable |
| `MOVIESITES_ENV_FILE` | `.env.example`; use a private copy for deployment-specific values |

The example uses init, a read-only container root, bounded CPU/memory/PIDs, local logging and a graceful stop period. These are configurable deployment limits, not a workload-capacity guarantee. JVM heap is only part of memory use; FFmpeg and native allocations require headroom.

## Testing and upgrades

Run the [reproducible checks](verification.md) against synthetic media, a separate data directory and an unused loopback port. Do not point an isolated test at production `/data`. Verify non-ASCII names, representative codecs, byte ranges, permissions, data locking and restart behavior on the target host. A healthy HTTP endpoint is not a full NAS audit.

Before upgrading, record the current immutable image reference, environment, numeric user and actual bind sources in private operator records. Test the candidate independently, then stop and wait for the current writer before starting the replacement with the same production mounts. Retain the previous image/configuration for rollback and verify the new service before removing recovery resources. A rollback must also stop the candidate before reopening the data root with the previous image.

Keep deployment locks and rollback details in your own operating environment. Service-scoped Compose commands avoid stopping unrelated services. Do not use `down -v` or global pruning as a routine update or recovery command, and never delete media/data directories to resolve a startup error.

## Exclusive data ownership

A Java advisory FileLock and an atomically created `/data/.moviesites.owner` directory protect one generated-data root from concurrent writers. A second instance fails startup without deleting the current owner's marker. Normal shutdown removes its own owner directory and releases the lock. Scaling several instances against one `/data` is unsupported.

After SIGKILL, host failure or an unclean exit, ownership evidence can remain. Startup fails closed rather than guessing that a slow writer is dead. Stop every container/process using that data root, serialize recovery with your deployment lock, inspect the owner evidence, and only then remove the stale owner directory before restarting one instance. Do not delete/recreate the advisory lock file or use a TTL to steal ownership. Restoring an older image does not remove these responsibilities.

## External access and PWA

The application is anonymous and read-only, so reachable visitors can browse and play supported media. Configure the intended network/authentication boundary before exposing it. For installable PWA behavior, serve the library and API/media from one browser-trusted HTTPS origin; development localhost is the usual exception. `MOVIESITES_PUBLIC_URL` configures introduction metadata and does not enable TLS. A proxy must preserve Range/206 streaming and the root manifest/service-worker paths. See [PWA configuration](configuration.md#install-as-an-app-pwa).

The PWA's offline screen reports a lost connection. It is not offline video, catalog or authenticated-session storage. Actual NAS behavior, browser installation and fullscreen/codec support must be tested on the target devices.
