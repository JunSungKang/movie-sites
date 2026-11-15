# Changelog

Release notes will be grouped by published version. The source build currently uses version `1.0.0`; that does not imply a matching published release or container registry artifact.

## Unreleased

### Added

- Optional child presentation preset using the same image and common code.
- Optional PWA installation with a bounded, bilingual reconnect screen; media and catalog responses are never stored by the service worker.
- English/Korean UI selection with `MOVIESITES_UI_LOCALE`, including all bundled themes, public introduction, install guidance and server messages.
- Optional filename topic profiles (`none` by default, `kids-education` on request), independent of visual themes and manual metadata.
- Scoped 10/20/30/40/50-minute random whole-video playback, with bounded on-demand subtree sampling, cancellation and a separate temporary playlist.
- Automatic fullscreen on user-initiated playback, with native/manual fallback and preserved final-popup closing.
- Introduction-only SEO with configured canonical origin, Open Graph, JSON-LD, robots and a single-page sitemap.
- Bounded/coalesced server directory and image caching, private ETag revalidation, session-restored list pages and verified-fingerprint static caching.
- Text-response compression without compressing video/JPEG payloads.
- Current-folder duration sorting across pages, duration dividers and labelled topic recommendations with external JSON overrides.
- Persistent browser-local video queue, sequential playback and recoverable playback controls.
- Explicit per-video JPEG thumbnails with automatic fallback.
- Standard Gradle Wrapper, Kotlin DSL build and wrapper validation in CI.
- English and Korean documentation, public configuration example and contribution/issue/PR guidance.

### Changed

- Stabilize the first library render through the shared catalog lifecycle while retaining loading feedback; improve child-theme secondary text contrast through its common palette.
- Use bounded, version-aware JPEG folder-cover previews without changing original files, the default cover endpoint or orientation-sensitive images.
- Organize Java features under `xyz.gg_jskang.moviesites` with public APIs, internal implementations and HTTP adapters; enforce dependency boundaries with architecture tests.
- Split native ES-module features into model, presenter and DOM view responsibilities, with public feature entry points and shared browser/API adapters.
- Propagate content fingerprints through the module import graph with `scripts/update-assets.mjs` and verify them without a frontend bundler.
- Remove unused state/styles/imports and align translated theme metadata, PWA colors, resource packaging and HTTP cache checks with current behavior.
- Use the neutral Midnight Stream theme by default and portable `compose.yaml` / `moviesites` Bake targets with configurable host identity and port.
- Publish bilingual installation, testing and maintainer guidance.
- Explicit UTF-8 locale in the application image and Compose service, with Korean/decomposed-path and environment-label regression checks across restart.
- Ubuntu 26.04 / Temurin 25 project-owned fallback bases, overridable Java image ARGs, Compose and Bake deployment with isolated verification.
- Build, tests and executable packaging use Gradle with JDK 25 and Spring Boot 4.1.2.
- Docker builds the application with the committed Gradle Wrapper.
- Mobile playback layout keeps native video controls inside the available viewport.
- Public documentation uses a general-purpose video-platform description and a single root documentation index.

### Removed

- Maven build descriptor and Maven Wrapper files from the active build.
