# Verification

[한국어](ko/verification.md) · [Documentation index](../README.md#documentation)


## Automatic fullscreen playback — 2026-11-14

Playback requests fullscreen from the original play interaction, before asynchronous media inspection. A non-dialog wrapper keeps the player controls available and remains fullscreen across queue items. Final completion exits fullscreen and closes the popup; manual close cancels pending playback/fullscreen work and restores the selected folder, scroll and queue. Native WebKit video fullscreen is attempted when playable media is available. Browsers that reject automatic fullscreen keep playing in a maximized viewport and expose a manual fullscreen control. Explicit user exit is respected; native-device behavior remains subject to browser policy.

Player adapter/unit regressions verify synchronous request timing, permission denial/manual recovery, native/prefixed support, late fullscreen completion after close, sequential completion, saved queue and existing playback cancellation. Actual macOS Chrome tests observed a player rectangle equal to the viewport, two real H.264 clips advancing to the second item with the expanded state retained, and final completion closing the popup, clearing the video source and preserving the two saved queue entries. At320×568 and812×375, the player filled the viewport and close/next controls remained inside it. The controlled browser rejected native fullscreen with TypeError not granted despite active user input, so native OS fullscreen is not claimed as verified; the maximized fallback was verified. Native iOS/Edge device testing remains outstanding.

## Native UTF-8 locale regression — 2026-11-09

- The affected running image had no UTF-8 locale: Java reported `file.encoding=UTF-8` but `native.encoding` and `sun.jnu.encoding` were `ANSI_X3.4-1968`. The mounted directories were correct; API folder paths and Korean environment labels contained replacement characters. Checking only health and ASCII fixtures had not covered this deployment condition.
- The project Dockerfile and Compose now explicitly set `LANG`/`LC_ALL` to `C.UTF-8`. Isolated verification covers Korean/decomposed/spaced nested paths, exact Korean environment labels, native/converted playback, thumbnails, range/token boundaries, FFmpeg, exclusive locking and restart. The existing operating image passed these checks with the corrected Compose environment on a separate reserved loopback port.
- Under the shared deployment flock, only the affected service was recreated with its existing image, network, numeric user and media/data mounts preserved. All three Korean collections, nested listings and labels matched; actual-media thumbnail/range playback and FFmpeg passed before and after restart. Original hashes/metadata for 88 files matched. Previous configuration/image and a stopped recovery container were retained; operations files and other services were not modified.

## Ubuntu 26.04 and Compose migration — 2026-11-09

- Project-owned fallback JDK/JRE images and the application image built from pinned official Temurin Ubuntu 26.04 sources. The actual engine was Docker Desktop Linux/arm64. The runtime reported Ubuntu 26.04, Temurin 25.0.5 and FFmpeg 8; Python, Node and javac were absent from the application image. Python 3.14.10 and Node 24.18.0 ran operational/repository tooling. JUnit 51 tests and JavaScript 24 tests passed; package/media/theme/hash/documentation checks passed.
- Synthetic direct H.264/AAC and converted MPEG4/PCM media, nested listing, JPEG thumbnails, 206 byte ranges, forged-token rejection, traversal/symlink rejection, read-only media/root, graceful stop and restart passed in an isolated Compose project/data root. Native Chrome played the converted fixture with readyState 4, advancing time, controls enabled and no media error.
- A real duplicate-writer test exposed that the existing advisory FileLock alone was insufficient across these Docker Desktop bind mounts. Atomic directory ownership was added alongside the original lock. Repeated cross-container rejection and normal shutdown/restart then passed. Unclean exits deliberately retain ownership evidence and require verified operator recovery; the Docker guide documents the limit.
- Under the shared macOS-compatible deployment flock, the previous image/container/configuration and actual bind sources were preserved. A first production validation command used an invalid FFmpeg duration spelling; automatic rollback restored the previous service. The command was corrected and shared with isolated verification, which passed before retry. The successful switch used project `platform`, service `movie-kids-web`, only host web port 20010, UID/GID 501:20 and the original read-only media/writable data mounts.
- Production HTTP listing/navigation, actual-media thumbnail/range playback and FFmpeg decode/encode passed before and after a controlled service restart; health became healthy. Original hashes/metadata for 88 external files matched. A competing process failed to acquire the held deployment lock. Actual macOS Chrome played a representative production video with readyState 4, advancing time and no video error. Previous images/containers and private recovery evidence were retained, without stopping other projects or deleting data volumes.

These are target-environment checks, not comprehensive physical Safari/Edge/mobile, huge-NAS outage/load, or independent external-network certification. The web port change creates a different browser origin: existing browser-local queues remain in the old origin's storage rather than being silently migrated or erased.

## Shared introduction theme — 2026-11-09

- The introduction now reuses the configured service theme CSS, logo, header/footer and packaged illustration. Its small layout stylesheet has no independent palette/font family; server-rendered HTML is visible without app JavaScript or catalog requests.
- Gradle and multi-stage Docker builds passed with 49 JUnit and 24 JavaScript tests. The new integration assertion checks child-toy and Midnight Stream selection, current asset fingerprints and absence of unresolved placeholders/catalog scripts. Existing SEO/CSP/Host/304 boundaries, package/theme and bilingual-documentation checks passed.
- Actual Docker HTTP checks passed for both runtime themes: four fingerprinted presentation assets each, immutable/304 behavior, gzip, canonical/JSON-LD/CSP and application/API noindex. Actual macOS Chrome matched the library's child-toy font, background token and header appearance; the introduction rendered without horizontal overflow at 390×844 and 320×568, with a 48-pixel primary link. The dark alternate theme also loaded its illustration and rendered without overflow at 320×568. Desktop and mobile screenshots remain local.

These are targeted layout/runtime checks, not exhaustive visual certification of every theme/browser/device.

## Introduction SEO and caching — 2026-11-09

- Gradle clean build and multi-stage Docker build passed: 48 JUnit tests and 24 JavaScript tests, without failures/skips. Theme, content-fingerprint, package/media exclusion and bilingual documentation checks passed.
- Tests cover fixed expiry, remaining freshness, weighted/entry LRU eviction, coalesced concurrent reads, in-flight limits, failed-load retry, bounded image reads, version changes and private ETag/304 responses. Sorted-query tests cover preparation reuse, ready-snapshot reuse, expiry and forced refresh. Session tests cover unchanged expiry, root/instance isolation and denied/quota storage fallback.
- Actual Docker HTTP checks verified introduction-only canonical/Open Graph/JSON-LD/CSP, Host-header independence, robots/sitemap scope and noindex headers on app/API/images/video. Lists and images returned body-free 304 responses; explicit refresh produced a different query ID. All 90 static resources matched the source; matching fingerprints received immutable caching, mismatches revalidated. Byte-range playback returned 206 and a forged token returned 403.
- In that local test, the app module was 25,024 bytes before compression and 7,938 bytes over negotiated gzip. These are payload sizes for this build, not a load-time/ranking guarantee. JPEG/video compression is excluded.
- Actual Chrome on macOS displayed folder covers, restored a recent list after reload, requested a fresh list with the refresh button, and rendered a duration-grouped video page. A representative video reached readyState 4 with native controls, advancing playback and no video error. The public introduction rendered without console warnings/errors. Screenshots and private media/configuration evidence remain local.

Search exclusion relies on compliant crawlers and is not access control. Search ranking/indexing delay, Core Web Vitals field data, hostile crawlers, large-NAS latency/load and physical Safari/Edge/iOS/Android certification were not measured.

## Duration ordering and topic tags — 2026-11-09

- Gradle clean build and the multi-stage Docker build passed. All 40 JUnit tests passed without failures/errors/skips, and 20 JavaScript tests passed. Existing 19-theme/package/hash/media-exclusion checks remained enabled.
- Server tests cover global order across continuation pages and more than one merge pass, unknown durations, signed/path-bound cursors, source-version and sidecar refresh, bounded queues, disk-limit failure/cleanup, slow-storage preparation and shared analysis capacity during playback work.
- The actual list-rendering adapter verifies visible duration headings, initial time badges, recommended/manual/unspecified topics and literal-text rendering. Boundary checks include exactly 300, 600 and 1200 seconds, fractions just below them, invalid durations and cancellation of preparation waits.
- A non-root/read-only-root candidate was tested through HTTP. Six synthetic H.264 clips at 299/300/599/600/1199/1200 seconds and one invalid input were globally ordered over four pages at page size two, with the invalid input last. Explicit tags, empty overrides and metadata refresh passed. Prepared playback returned a correct 32-byte range with 206; a forged stream token was rejected with 403.
- Representative external folders returned increasing real durations, and all 89 static resources matched the source. English/Korean documentation links and environment/build references passed.

Direct browser control was unavailable in this turn. New screenshot/mobile-layout/native-player visual observations were therefore **not performed**; actual app event-wiring adapters and HTTP checks are not represented as real browser QA. Earlier browser evidence below remains historical. NAS outage/load testing and physical browser/device certification remain outside this change's measured coverage.

## Gradle and documentation migration — 2026-11-08

The pre-migration working tree's queue, per-video thumbnail and mobile-player improvements were preserved in a separate baseline commit. The migration then changed the build/packaging and public documentation; it did not replace those runtime sources or original media.

- JDK 25 and the standard Gradle 9.9.0 Wrapper built the executable JAR successfully. The distribution checksum and official wrapper JAR checksum were verified before use. Spring Boot remains 4.1.2.
- The 29 JUnit tests passed without failures, errors or skips. Coverage includes path/symlink boundaries, bounded browsing/cursors, byte ranges, generated-cache budgets, startup settings, folder covers and explicit-thumbnail selection/fallback.
- All 34 packaged runtime dependency JAR filenames matched the pre-migration baseline. The single-JAR/static-frontend architecture was retained.
- The 16 JavaScript checks passed for cache TTL/LRU/size and cursor history, queue persistence/limits, sequential playback, cancellation, autoplay recovery and mobile player wiring. These adapter checks do not certify every browser policy.
- Theme, content-hash, Git exclusions and JAR separation checks passed. Actual videos, collection images and generated data stay outside the source package. The 19 bundled theme IDs are a compatibility baseline, not a maximum catalog size.
- The Gradle multi-stage Docker image built and a non-root, read-only-root candidate started with separate external media/data mounts and an environment-selected theme. Health, config, browsing, path rejection and static-resource revalidation smoke checks passed.

The documentation checker passed for 29 Markdown files and 155 local links, including heading targets, English/Korean guide counterparts, environment names/examples and active Gradle build references. The final container matched all 88 static source files, loaded the public environment-file defaults, and played representative H.264/AAC media in the in-app Chromium browser with readyState 4, advancing time, native controls and no video error. Command, container and service checks are repeated against the final tree before integration. Repository checks and App approval gate the current head; a historical successful run is not approval of a new head.

## Inherited UI evidence

The preserved runtime baseline was previously inspected in the macOS in-app Chromium browser: 19 themes at 320×568 and 812×375 playback viewports, queue ordering/restoration, sequential completion, late-response cancellation and native video controls. Real H.264/AAC media reached readyState 4 with advancing playback and no video error. Earlier detailed reports remain in Git history rather than advertising obsolete build commands here.

Those observations apply to that baseline and environment. Gradle changes do not expand them into new browser/device certification. Real media frames, private paths, deployment configuration and detailed local evidence are not published in the repository.

## Limits

Safari/iOS and Edge/Android device combinations, accessibility certification, prolonged load, huge-directory capacity, actual NAS stalls/disconnections and disk exhaustion were not comprehensively exercised by this migration. Native secure-directory support must be checked on the target filesystem. Container example limits are not a service-capacity guarantee. Public-host verification from the development environment is not an independent external-network measurement.
