# Contributing to MovieSites

Thank you for helping improve a self-hosted video platform. Code, bug reports, tests, documentation, translations and theme contributions are welcome. English is preferred for shared project discussion; help with wording and Korean-language clarification is welcome too.

## Before starting

Search existing issues and pull requests. Open an issue before a large feature, architecture change, dependency upgrade or public API change so maintainers can discuss scope. Small fixes and documentation improvements can go straight to a pull request. Report sensitive issues through the [security policy](SECURITY.md).

## Development workflow

1. Fork the repository and clone your fork.
2. Create a focused branch from the current `main`.
3. Follow the [development guide](docs/development.md). Preserve existing public paths, configuration and behavior unless the change explicitly calls for a migration.
4. Add or update checks that can detect the behavior you changed. Keep unrelated formatting and refactoring out of the change.
5. Run the checks below, update affected documentation and open a pull request against `main`.

```bash
./gradlew clean build
bash scripts/check.sh
python3 -B scripts/check-docs.py
```

Describe the problem, the resulting behavior, how it was checked and any limitations. Include before/after screenshots for visible changes using synthetic or redistributable example media. Link related issues. If a check cannot run, explain the missing prerequisite rather than claiming it passed.

## Contribution areas

- **Code:** keep the single-application runtime and bounded media/storage work. Use the existing JDK 25/Spring Boot stack and static frontend.
- **Themes:** follow [the theme guide](docs/themes.md#adding-a-theme); add a catalog entry, manifest, stylesheet and original/local SVG assets. Do not copy the application for each theme.
- **Documentation:** use English as the canonical text, put detailed information in the relevant guide and link it from the root README. Avoid duplicating configuration tables or entire setup procedures.
- **Translations:** update `README.ko.md` and matching files under `docs/ko/` with the English source change. New languages should use their language code and keep the same guide filenames. Documentation translation does not imply that the application UI is localized.
- **Tests:** test observable behavior and important error paths. Browser/media adapters do not certify all browser decoders or storage devices.

## Project conventions

Use the committed Gradle Wrapper. Java uses the normal `src/main/java`, `src/main/resources` and `src/test/java` layout. Follow nearby code and avoid unrelated reformatting. Use descriptive commit messages and keep each change reviewable; an issue number or special commit-prefix scheme is not mandatory.

Only contribute work you have the right to contribute under the project license. Declare new dependencies and asset licenses in the relevant notice file. Do not commit original videos, actual collection covers, generated playback files, credentials, local environment files, private host addresses or build caches. `movie/` tracks only `.gitkeep`; public configuration examples belong in `.env.example`.

## Review and integration

Anyone can submit a fork pull request using normal GitHub tooling. No personal Codex installation, GitHub App key or maintainer credential is required from contributors. A new push may require another review.

The canonical repository currently enforces an account-bound maintainer PR and current-head GitHub App approval gate. Until that policy supports direct external integration, maintainers may carry an accepted contribution into a maintainer-owned integration PR, linking the original PR and retaining contributor attribution. This is a current maintainer-policy constraint, not a requirement to create another personal account or obtain bot credentials. See [maintainer procedures](docs/maintainers.md).

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md). Maintainers may request a smaller scope, tests, documentation or a correction before integrating a contribution.
