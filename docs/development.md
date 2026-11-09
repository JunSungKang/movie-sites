# Development

[한국어](ko/development.md) · [Documentation index](../README.md#documentation)

## Prerequisites and setup

Use JDK 25, Node.js 24 and Python 3.14.9 or later for the full repository checks. Install FFmpeg/ffprobe to exercise local media processing, or use the Docker image. A global Gradle installation, frontend development server and database are not required.

Fork/clone the repository, create a focused branch and run:

```bash
./gradlew clean build
bash scripts/check.sh
python3 -B scripts/check-docs.py
```

`build/libs/moviesites.jar` is the executable artifact. Java test results are under `build/test-results/test/` and the HTML report under `build/reports/tests/test/`. `./gradlew test` runs server tests; `node --test scripts/cache.test.mjs scripts/playback.test.mjs scripts/player-ui.test.mjs scripts/library.test.mjs` runs the JavaScript tests. Package checks require a freshly built JAR. See [CONTRIBUTING](../CONTRIBUTING.md) for the PR workflow.

## Source layout

| Path | Responsibility |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/` | Kotlin DSL build, project identity and verified Gradle Wrapper |
| `src/main/java/io/moviesites/` | Configuration, HTTP API, storage, browsing and media processing |
| `src/main/resources/application.yml` | Environment-backed application settings |
| `src/main/resources/static/js/` | Common browsing, response cache, queue and player behavior |
| `src/main/resources/static/css/app.css` | Shared functional/accessible UI rules |
| `src/main/resources/static/themes/` | Catalog, preset copy/styles/SVGs and preview |
| `src/test/java/io/moviesites/` | Server regression tests |
| `scripts/` | Frontend, theme, documentation, package and runtime checks |
| `docs/` | Detailed guides linked from the root README |

The application follows the standard Java/Gradle source layout. The current small Java codebase stays in one base package; new package boundaries should follow actual maintenance needs rather than a required directory taxonomy.

## Running and testing changes

Use the [getting-started guide](getting-started.md) for the JAR or run `./gradlew bootRun` with explicit media/data roots. Every concurrent instance needs a distinct data root and port. Tests use project-local temporary files and remove their fixtures. Real media must never become a committed fixture.

`bash scripts/check.sh` checks JavaScript syntax, cache/queue/player behavior, theme assets, content hashes, Git exclusions and JAR/data separation. `scripts/check-docs.py` checks local documentation links, public settings examples, locale counterparts and build references. Visible UI changes also require real browser inspection; native codec/device, NAS failures and load tests are separate from adapter/unit tests.

After changing a versioned UI asset, update its content-hash query reference in `index.html` or its importing module. Keep the root media directory, generated data, build output and credentials untracked. Do not change stream authorization or filesystem boundaries as incidental cleanup.

Runtime smoke check against an already running test instance:

```bash
python3 -B scripts/verify-runtime.py http://127.0.0.1:8080
```

Gradle uses its normal user cache unless `GRADLE_USER_HOME` is set. For workspace-bounded automation, set it to `$PWD/.cache/gradle`, create `$PWD/.cache/tmp`, and set `JAVA_TOOL_OPTIONS` to `-XX:-UsePerfData -Djava.io.tmpdir=$PWD/.cache/tmp`. This keeps that automation's caches/temp files inside the checkout; it is not a special contributor authentication step.

## Themes and documentation

Use [the theme guide](themes.md#adding-a-theme) for preset contributions. English is the documentation source, `README.ko.md` is the root Korean translation, and `docs/ko/` mirrors guide filenames. Keep both languages updated in the same PR. Actual UI copy is currently Korean and is separate from documentation translation. Historical SDD metadata is explained in [maintainer notes](maintainers.md).

Use Python 3.14 for operational/verification scripts and Node 24 for JavaScript checks. Neither Python nor Node is added to the application runtime image.
