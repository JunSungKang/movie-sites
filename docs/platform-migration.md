# Compose migration and rollback

[한국어](ko/platform-migration.md) · [Docker guide](docker.md)

This procedure changes only MovieSites (`movie-kids-web`). Preserve original media, generated data, the previous image and the old container. Never run an isolated proof against production `/data`; never use `down -v`, global prune, or stop another platform service.

## Inspected docker-run → Compose mapping

| Manual configuration | Compose configuration |
|---|---|
| `moviesites` | project `platform`, service `movie-kids-web`, normally `platform-movie-kids-web-1` |
| Ubuntu 24.04 Temurin 25 image | project-owned Ubuntu 26.04 Temurin 25 JDK build / JRE runtime bases |
| Host web port 18081 → 8080 | reserved web 20010 → 8080; UI/API remain one application |
| Explicit host bind address | retain the same address through `MOVIESITES_BIND_ADDRESS` |
| UID/GID `501:20` | `MOVIESITES_UID=501`, `MOVIESITES_GID=20` |
| project `movie/` → `/media`, read-only | same actual bind source and read-only mode |
| project `runtime/` → `/data`, writable | same actual bind source, kernel lock plus atomic owner guard |
| init / read-only root | `init: true`, `read_only: true` |
| 2 GiB / 2 CPUs / 128 PIDs | `mem_limit: 2g`, `cpus: 2`, `pids_limit: 128` |
| unless-stopped / local logs | same restart policy and log driver |
| engine default stop timeout | explicit SIGTERM and 30-second grace for Spring graceful shutdown |
| engine bridge | Compose-managed bridge; no added cross-project dependency |
| theme, labels, cache/JVM settings | preserve operator values in a private raw environment file |
| original public URL | update only its host port for the new public web mapping |

Build [project fallback bases and application](docker.md#build-images), then run isolated verification. `JAVA_BUILD_IMAGE` / `JAVA_RUNTIME_IMAGE` accept compatible shared bases, but their absence does not block project-owned fallback builds. No shared tag is overwritten. The application uses FFmpeg/certificates from the project image, with no database/Python/Node runtime.

## Serialized switch

1. Use Python 3.14. Save the old image ID, complete container inspection and its numeric user/mounts. Keep originals and data in place. Validate the new Compose model before stopping anything.
2. Hold a real `fcntl.flock(LOCK_EX)` on the agreed shared `../operations/run/deploy.lock`. Create the missing parent/file if authorized; do not delete/recreate the lock inode to release it. The tool proves a competing process cannot acquire it.
3. Stop the verified old container gracefully, wait for exit, then rename it to the recorded `moviesites-before-ubuntu26-*` rollback name. Only now may the new service open the same data root.
4. Start only `platform/movie-kids-web` from the tested immutable image. Preserve actual mount sources, user, limits and application settings; inherited Java image metadata comes from the new base. Check health, actual nested listings, thumbnails, byte ranges/token rejection and real-media FFmpeg decode/encode.
5. Perform a controlled service-scoped stop/start, repeat production checks, wait for healthy status and verify original-media hashes. Retain the stopped previous container/image and private recovery records.

```bash
python3.14 -B scripts/deploy-platform.py \
  --image movie-kids:ubuntu26.04 \
  --qa-report .codex-work/platform-release/qa-EXACT/verification.json \
  --lock ../operations/run/deploy.lock
```

The report must match both the immutable image ID and Compose file hash. The script checks for the existing `moviesites` first; it is an initial run-to-Compose migration, not a general unattended updater. Private records under `.codex-work/platform-release/` include `production-before.json`, application/Compose environment files and `deployment.json`. Configuration files are mode 0600 and are not Git content. The shared lock covers stop, rename, start, validation, restart and automatic rollback.

## Recovery

The tool stops/removes only its failed candidate, restores the original container name if renamed, starts the original image/configuration, and checks its old web address before releasing the deployment lock. Data volumes and originals are never deleted. The preserved old container is authoritative for its full configuration.

For a later manual rollback, acquire the same shared lock first. Read `deployment.json` for the exact candidate ID and old rollback name, stop/wait/remove only that candidate, rename the preserved container back to `moviesites`, then start it and verify the previous address. Do not rebuild/retag the old image or change its mounts. A stale `.moviesites.owner` may be removed only after verifying every writer is stopped, as described in [data ownership](docker.md#exclusive-data-ownership). The old pre-guard image does not enforce that new directory guard; never run it beside the new service.
