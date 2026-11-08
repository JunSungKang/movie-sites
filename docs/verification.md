# Verification

[한국어](ko/verification.md) · [Documentation index](../README.md#documentation)

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
