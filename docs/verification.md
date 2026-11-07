# Verification record

Date: 2026-11-07. These observations describe the tested environment; structural SDD validation is not a quality certificate.

## Automated checks

- JDK 25 / Spring Boot 4.1.2 Maven verify: 18 JUnit tests passed. Coverage includes external-root separation, traversal/absolute path rejection, regular-file secure opens, file/directory symlink rejection, immediate-only Unicode listings, unique bounded continuation cursors, cursor replay/wrong-directory rejection, byte range boundaries, first-audio/attached-cover selection, source-version invalidation, signed playback mode/source binding independent of job-map lifetime, and a 10,000-file directory returning a bounded 48-item batch. Six cache/recovery regressions also passed: growing output reclaims completed cache and permits later requests, stream reference pins prevent eviction, restart removes only recognized interrupted work, oversized work fails safely, eviction progresses beyond its 1024-candidate window, and the data-root lock rejects a second owner and permits reopening after close.
- Five Node tests passed: fixed cache expiry without sliding TTL, LRU response eviction, aggregate entry/byte limits, path/page-specific invalidation, and 10,000 page transitions with a maximum 20 retained cursors.
- A Spring MVC integration regression verified HTTP 200, `Cache-Control: no-store` and no `Last-Modified` for HTML/CSS/ES modules even with a future `If-Modified-Since`. This prevents a fixed reproducible JAR timestamp from incorrectly returning 304 after an update. The public runtime smoke repeats those conditional requests.
- Package validation also checks content-hash query parameters for the HTML JS/CSS references and the cache module import. With old resources still cached in the in-app browser, loading a fresh final HTML document selected the new hashed URLs and prepared the video at readyState 4 with a signed stream token, without a force reload.
- Frontend JavaScript syntax, tracked-media exclusions and JAR content separation passed `scripts/check.sh`.
- The external runtime smoke test passed health/config/current-folder listing, traversal rejection and missing-directory responses.
- The reusable global GitHub flow has 13 passing tests. Actual current-origin checks rejected wrong repository, actor and numeric repository ID. Initial-mode reuse after the first push was rejected. Skills passed their structural validator.

## Container and storage

- Multi-stage Dockerfile built successfully on Docker Desktop linux/arm64.
- Runtime starts with a read-only container root filesystem, a read-only `/media` bind mount, and writable external `/data`.
- Container limit: 2 GiB memory and 2 CPUs. One post-processing snapshot showed 213.7 MiB, 0.22% CPU and 38 processes/threads. This snapshot is not a peak-memory guarantee or a concurrency benchmark.
- The health check became healthy. An attempted test-file write to `/media` failed with a read-only filesystem error.
- A separate disposable container with an unavailable media root returned HTTP 503 `STORAGE_UNAVAILABLE` while application health remained HTTP 200. The test container was removed. This simulates missing storage; a real SMB/NFS outage has not been tested.
- After the cache fixes, a disposable container with a 1 MiB generated-cache budget started with a 950,000-byte completed cache, a 900,000-byte abandoned partial file and an interrupted process log. Startup removed the recognized abandoned work; actual WebM-to-MP4 conversion reclaimed the old completed cache and reached `ready`. The test container was removed. The public container was replaced with this build and its runtime smoke checks passed again.
- Original and generated videos remain outside the JAR/image. The JAR is approximately 19 MiB; the image is approximately 1 GiB because it includes the Java runtime and Ubuntu FFmpeg dependencies. Adding originals does not increase either artifact.

## Media processing

All test videos are clearly labeled synthetic samples generated locally with FFmpeg; no private user video or downloaded third-party footage was exposed.

- H.264/yuv420p/AAC MP4: original playback ready, 1280x720, 8 seconds.
- H.264/AAC MOV: remuxed MP4 playback ready, original preserved.
- VP9/Opus WebM: transcoded H.264/AAC MP4 playback ready, 640x360, approximately 6 seconds.
- All three returned HTTP 206 with `Content-Range: bytes 0-1023/<length>` and exactly 1024 response bytes.
- Thumbnail generation completed for the samples.
- Actual HDR material, corrupted-file recovery, sustained concurrent playback and very large real NAS directories remain untested.

## Browser and interface

- Actual Google Chrome: original MP4 and converted WebM played with native controls. The video element reported readyState 4, paused false, expected dimensions/duration and no decoder error. Seeking reached the expected end position.
- A pre-policy Chrome session retained old JavaScript and required one force reload during deployment validation. After that reload the final build sent signed playback parameters and played the converted sample with readyState 4 and no decoder error. New static responses use the tested no-store policy; directory-data caching remains independent.
- Actual Safari 26.6.2: converted video played from 0 to 6 seconds and its native scrubber moved back to 3 seconds. The audio control was unmuted and the generated source includes an audio track; subjective sound quality was not assessed.
- Desktop: horizontal journal rows had separate thumbnail/text columns. Mobile viewport 390px: rows used one column, sidebar was hidden and document width did not exceed the viewport. Temporary viewport overrides were reset.
- Folder navigation and revisit displayed the cached-list state. Explicit refresh displayed newly added folders.
- Microsoft Edge is not installed here and was not run. The implementation uses standard HTML video and a common MP4/H.264/AAC playback format, but this is not a claim of an actual Edge test.

## External reachability

Public application: http://175.195.62.165:18081/

An independent Check-Host HTTP probe requested only the public health URL, with two nodes:

- Tokyo, Japan: HTTP 200, 0.081 seconds.
- Belgrade, Serbia: HTTP 200, 0.553 seconds.
- Both observed destination IP 175.195.62.165.
- Request ID: `561f8e94k8fc`.
- Report: https://check-host.net/check-report/561f8e94k8fc

This deployment is an anonymous read-only service on the current host. It uses a separate port and leaves pre-existing 80/443 services unchanged. Availability requires the host, Docker and source storage to remain available. Docker restart policy is `unless-stopped`. No custom domain or TLS termination has been configured.
