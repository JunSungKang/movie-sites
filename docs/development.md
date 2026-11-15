# Development

[한국어](ko/development.md) · [Documentation index](../README.md#documentation)

## Prerequisites and setup

Use JDK 25, Node.js 24 and Python 3.14.x for the full repository checks. Install FFmpeg/ffprobe to exercise local media processing, or use the Docker image. A global Gradle installation, frontend development server and database are not required.

Fork/clone the repository, create a focused branch and run:

```bash
./gradlew clean build
bash scripts/check.sh
python3 -B scripts/check-docs.py
```

`build/libs/moviesites.jar` is the executable artifact. Java test results are under `build/test-results/test/` and the HTML report under `build/reports/tests/test/`. `./gradlew test` runs server tests; `node --test scripts/*.test.mjs` runs all JavaScript tests, including UI locales and PWA behavior. Package checks require a freshly built JAR. See [CONTRIBUTING](../CONTRIBUTING.md) for the PR workflow.

## Source layout

| Path | Responsibility |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/` | Kotlin DSL build, project identity and verified Gradle Wrapper |
| `src/main/java/xyz/gg_jskang/moviesites/` | Application entry, feature APIs, implementations and HTTP adapters |
| `src/main/resources/application.yml` | Environment-backed application settings |
| `src/main/resources/static/js/{catalog,player,queue,random-playback,installation}/` | Feature models, presenters, views and public `index.js` entry points |
| `src/main/resources/static/js/shared/` | Public API, browser, cache, UI and translation adapters |
| `src/main/resources/static/js/app.js` | Feature construction and callback wiring |
| `src/main/resources/static/css/app.css` | Shared functional/accessible UI rules |
| `src/main/resources/static/themes/` | Catalog, preset copy/styles/SVGs and preview |
| `src/test/java/xyz/gg_jskang/moviesites/` | Server regression and compiled architecture tests |
| `scripts/` | Frontend, theme, documentation, package and runtime checks |
| `docs/` | Detailed guides linked from the root README |

The application follows the standard Java/Gradle source layout and builds one executable JAR. Java features expose their supported types at the feature package root, keep implementation details in `internal`, and put HTTP adapters in `web`. Cross-feature code depends on public types, never another feature's `internal` or `web` package. See the [code boundaries](architecture.md#code-boundaries) for responsibilities and enforcement limits.

Browser features expose factories through their own `index.js`. Models hold state and policy; presenters coordinate injected APIs and views; views render DOM and report UI events. Features communicate through callbacks wired in `app.js`, not through imports into each other. Shared groups expose `shared/<group>/index.js`; theme modules also use those public entries. A folder name alone does not enforce these rules: visibility, explicit entry points and architecture tests do.

## Running and testing changes

Use the [getting-started guide](getting-started.md) for the JAR or run `./gradlew bootRun` with explicit media/data roots. Every concurrent instance needs a distinct data root and port. Tests use project-local temporary files and remove their fixtures. Real media must never become a committed fixture.

`bash scripts/check.sh` checks JavaScript syntax, dependency boundaries, cache/queue/player behavior, theme assets, content hashes, Git exclusions, packaged resource bytes and JAR/data separation. `scripts/check-docs.py` checks local documentation links, public settings examples, locale counterparts and build references. Visible UI changes also require real browser inspection; native codec/device, NAS failures and load tests are separate from adapter/unit tests.

After changing a versioned UI asset, run:

```bash
node scripts/update-assets.mjs
node scripts/update-assets.mjs --check
node --test scripts/architecture.test.mjs scripts/assets.test.mjs
```

The updater propagates content fingerprints from dependencies through native module imports/re-exports to the localized HTML entry points, and updates the bounded offline-cache revision. `--check` reports stale references without writing. Use literal module specifiers so the dependency graph can be inspected; variable dynamic-import paths are rejected. Keep localized manifest/preview resources synchronized when changing shared settings or assets. Keep the root media directory, generated data, build output and credentials untracked. Do not change stream authorization or filesystem boundaries as incidental cleanup.

The library entry pages also receive native module preload hints derived from their module scripts' static imports. There is no second dependency list to maintain; dynamic imports stay on demand. The generated hints are committed with the other asset references, so running or packaging the application still does not require Node.js.

Java architecture tests inspect compiled dependencies for forbidden cross-feature access. JavaScript architecture tests check the full packaged module graph, public entries, cycles, Presenter DOM access, View network access and composition-root IO. The import extractor is checked against Node's V8 module parser; deliberate invalid examples verify the guard detects violations. These checks enforce declared structural rules, not every possible runtime behavior or business-policy mistake.

Runtime smoke check against an already running test instance:

```bash
python3 -B scripts/verify-runtime.py http://127.0.0.1:8080
```

Gradle uses its normal user cache unless `GRADLE_USER_HOME` is set. For workspace-bounded automation, set it to `$PWD/.cache/gradle`, create `$PWD/.cache/tmp`, and set `JAVA_TOOL_OPTIONS` to `-XX:-UsePerfData -Djava.io.tmpdir=$PWD/.cache/tmp`. This keeps that automation's caches/temp files inside the checkout; it is not a special contributor authentication step.

## Themes and documentation

Use [the theme guide](themes.md#adding-a-theme) for preset contributions. English is the documentation source, `README.ko.md` is the root Korean translation, and `docs/ko/` mirrors guide filenames. Keep both languages updated in the same PR. The UI supports `MOVIESITES_UI_LOCALE=en` (default) and `ko`. Packaged UI keys live in `static/js/shared/i18n/index.js`; media names and operator tags remain literal. Use [maintainer notes](maintainers.md) for release responsibilities; task plans and private deployment evidence stay outside published documentation.

Use Python 3.14 for operational/verification scripts and Node 24 for JavaScript checks. Neither Python nor Node is added to the application runtime image.

## PWA changes

PWA behavior is in `static/js/installation/`, its `static/js/pwa.js` bootstrap, `static/service-worker.js` and `static/manifest.webmanifest`; `static/pwa/` contains brand icons and offline-only assets. These paths are relative to `src/main/resources/`. `scripts/pwa.test.mjs` checks real installation event wiring, standalone/insecure behavior and service-worker network/privacy boundaries. Server PWA tests check HTTP types, cache revalidation, icon sizes, opacity and worker scope.

When changing an offline asset, update the worker cache suffix to the first16 SHA-256 hex characters of the concatenated bytes of `offline.html`, `pwa/offline.css` and `pwa/icon-192.png`, in that order. Do not add media, catalog responses or tokens to its cache. The package check verifies that cache revision and the packaged PWA bytes. Keep icon graphics within the maskable safe zone; `pwa/icon.svg` is the editable vector source.

Inspect a localhost or trusted-HTTPS test instance in a real browser: check manifest/install eligibility, install or follow platform guidance, launch standalone, verify playback and Range seeking, then disconnect only the test instance and check the generic fallback/reconnection. Test at a narrow viewport as well. iOS installation and codec support still require a real supported device; do not label desktop emulation as a native iPhone test.
