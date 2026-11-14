# Changelog

Release notes will be grouped by published version. The source build currently uses version `1.0.0`; that does not imply a matching published release or container registry artifact.

## Unreleased

### Added

- Optional PWA installation with a bounded, bilingual reconnect screen; media and catalog responses are never stored by the service worker.
- English/Korean UI selection with `MOVIESITES_UI_LOCALE`, including all bundled themes, public introduction, install guidance and server messages.
- Optional filename topic profiles (`none` by default, `kids-education` on request), independent of visual themes and manual metadata.

### Changed

- Use the neutral Midnight Stream theme by default and portable `compose.yaml` / `moviesites` Bake targets with configurable host identity and port.
- Replace per-host migration notes and internal plans with bilingual installation, testing and maintainer guidance; existing Git history is preserved.

### Existing features

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

- Explicit UTF-8 locale in the application image and Compose service, with Korean/decomposed-path and environment-label regression checks across restart.

- Ubuntu 26.04 / Temurin 25 project-owned fallback bases, overridable Java image ARGs, Compose and Bake deployment with isolated verification and locked migration/rollback tooling.

- Build, tests and executable packaging use Gradle with JDK 25 and Spring Boot 4.1.2.
- Docker builds the application with the committed Gradle Wrapper.
- Mobile playback layout keeps native video controls inside the available viewport.
- Public documentation uses a general-purpose video-platform description and a single root documentation index.

### Preserved

- One application/JAR, static frontend, 19 bundled themes and folder-driven browsing without a database or full-tree index.
- External media/data separation and read-only original media access.

### Removed

- Maven build descriptor and Maven Wrapper files from the active build.
