# Testing and compatibility

[한국어](ko/verification.md) · [Documentation index](../README.md#documentation)

This guide describes reproducible checks and their limits. It is not an operating diary, a capacity guarantee or a claim that every browser, NAS and device has been tested. Record results against the exact commit/image and target environment; do not reuse a previous release's result as evidence for changed files.

## Repository checks

Install the prerequisites in [development](development.md), then run:

```bash
./gradlew clean build
bash scripts/check.sh
python3 -B scripts/check-docs.py
```

| Check | Expected coverage |
|---|---|
| Gradle build/tests | Java compilation, storage boundaries, paging/sorting, metadata, resource budgets, HTTP/media behavior and packaged application |
| `scripts/check.sh` | JavaScript syntax and regressions, theme/catalog validation, content hashes, PWA cache boundaries, Git exclusions and JAR/media separation |
| `scripts/check-docs.py` | Local links/anchors, English/Korean guide pairs, documented environment settings, public examples and build references |

Inspect the actual test output and failures. Unit/adapter tests do not establish real native-player, installation, filesystem or network behavior. Test counts change with the codebase and are not a compatibility certification. Public images or download commands should be documented only after the corresponding artifact exists.

## Isolated runtime checks

Use synthetic media and an otherwise disposable generated-data directory. Never run smoke tests against production `/data` or modify an original collection to make a test pass. Build/run the candidate using the [Docker guide](docker.md) or [JAR setup](getting-started.md), on an unused loopback port:

```bash
python3 -B scripts/verify-runtime.py http://127.0.0.1:8080
```

The smoke tool checks the running endpoint; review its output rather than assuming every item below is automated. Verify the following for a candidate on its intended host:

- Application health/configuration and current-folder navigation, including non-ASCII/decomposed names, spaces, empty directories and unsupported files.
- Duration-sorted preparation/pages, fixed-expiry cache reuse, explicit refresh, invalid/expired cursors and accurate duration boundaries.
- A direct H.264/AAC MP4 and a decodable source that needs complete playback preparation; inspect the generated result with FFmpeg/ffprobe.
- Folder/video JPEG validation, explicit sidecars and thumbnail fallback, metadata limits and optional topic-profile selection.
- GET/HEAD and valid/invalid single byte ranges; changed-source and forged playback-token rejection. Tokens are consistency checks, not login credentials.
- Path traversal/symlink rejection, separate media/data roots, read-only media/container roots and refusal of a competing data writer.
- Graceful stop/restart, generated-work recovery and original-media preservation. Use a separate test data root when exercising unclean-exit recovery.

Keep fixture media, screenshots, detailed reports, deployment state and private paths outside Git. Retain private rollback evidence separately from public release notes. `/api/health` reports application response status, not storage-wide health or codec readiness.

For an isolated container test with Python 3.14 and an already built image:

```bash
python3 -B scripts/verify-platform.py --image moviesites:ubuntu26.04 --port 8081
```

The helper creates synthetic media and a separate data root, uses the non-root host UID/GID and a unique Compose project, and checks its candidate without mounting production data. Choose an unused port. Its generated reports and any `--keep` recovery details are local operator artifacts, not public documentation.

## Browser and PWA checks

Use real target browsers/devices as well as automated regressions:

| Area | Manual acceptance |
|---|---|
| Browsing | Desktop/narrow/short landscape layouts, long names, keyboard focus, notices, theme selection and no clipped controls |
| Native playback | Audible output where present, advancing playback time, seek/range behavior, original aspect ratio and supported fullscreen controls |
| Queue/random playback | Preserve the saved queue, advance on natural completion, close after the last item, cancel late preparation and respect autoplay denial |
| Browser storage | Denied/quota storage falls back without losing current-tab controls; origin/root changes isolate saved data |
| PWA installation | Test platform-specific promotion/manual installation on a browser-trusted HTTPS origin or eligible localhost |
| Connection loss | Navigate offline after the service worker is active and confirm the bounded connection-lost fallback; reconnect and retry |
| Private response exclusion | Confirm service-worker storage does not retain catalog/API responses, images from private media, video, signed stream URLs or Range responses |

The PWA does not provide offline video or an offline catalog. A public HTTP IP address and a browser warning bypass do not establish production installation eligibility. Native fullscreen/autoplay and codec support depend on OS/browser policy. A maximized in-page player is not proof that OS fullscreen succeeded. The supported UI selections are English and Korean; do not infer additional complete locales from theme names or documentation.

## Support boundaries

- Native-server targets are Linux/macOS with a media provider supporting Java secure directory streams; Windows users can use Linux containers and must validate path sharing.
- NAS/network mounts require target-specific permission, filename and secure-directory tests. Blocking OS calls can outlive application timeout/cancellation. Neither paging nor bounded memory guarantees huge-folder latency.
- Test sustained concurrent playback, a large single directory, slow/disconnected storage, processing saturation and disk exhaustion before relying on a deployment's capacity.
- Listed file extensions do not certify every codec. Damaged/encrypted sources can fail. FFmpeg prepares complete compatible files; it is not an editor or live HLS service.
- Anonymous read-only HTTP access and noindex headers do not restrict who can view reachable media. Validate the deployment's own access/TLS boundary separately.
- Browser/device matrices, accessibility certification and load targets need their own results. These commands alone do not certify Safari/iOS, Edge/Android, all themes or arbitrary hardware.

## Reporting results

For a public bug or release note, include the commit/image reference, relevant component versions, OS/browser and filesystem type, reproduction steps, expected/observed behavior and known limits. Redact media identities, tokens, personal paths and deployment topology. Use [SECURITY.md](../SECURITY.md) for sensitive reports and [maintainer procedures](maintainers.md) for releases. Historical operation logs and task approvals are not public project documentation.
