# Changelog

Release notes will be grouped by published version. The source build currently uses version `1.0.0`; that does not imply a matching published release or container registry artifact.

## Unreleased

### Added

- Persistent browser-local video queue, sequential playback and recoverable playback controls.
- Explicit per-video JPEG thumbnails with automatic fallback.
- Standard Gradle Wrapper, Kotlin DSL build and wrapper validation in CI.
- English and Korean documentation, public configuration example and contribution/issue/PR guidance.

### Changed

- Build, tests and executable packaging use Gradle with JDK 25 and Spring Boot 4.1.2.
- Docker builds the application with the committed Gradle Wrapper.
- Mobile playback layout keeps native video controls inside the available viewport.
- Public documentation uses a general-purpose video-platform description and a single root documentation index.

### Preserved

- One application/JAR, static frontend, 19 bundled themes and folder-driven browsing without a database or full-tree index.
- External media/data separation and read-only original media access.

### Removed

- Maven build descriptor and Maven Wrapper files from the active build.
