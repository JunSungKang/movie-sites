# Themes

[한국어](ko/themes.md) · [Documentation index](../README.md#documentation)

All presets use the same library and player behavior. Age groups organize optional visual styles; they do not restrict who may choose a theme and no age is collected. The neutral default is `20s-midnight-stream`. `child-toy` remains available as an optional toy-like folder deck. Theme selection does not enable a topic profile. UI language is selected independently with `MOVIESITES_UI_LOCALE=en` or `ko`; existing theme IDs stay the same in both languages.

## Choosing a preset

Set `MOVIESITES_THEME` before startup, for example `MOVIESITES_THEME=20s-soft-minimal`, and restart the process or recreate the container. Selection is case-sensitive and unknown IDs fail startup. Choosing an existing packaged theme does not require a rebuild.

Open `/themes/preview.html` on your running service to view the catalog. Its real-page links use `/?preview-theme=<registered-ID>`. This is a tab-local preview, clearly marked, and does not change the server setting. Invalid preview IDs fall back to the configured default.

## Bundled presets

| Group | ID | Design |
|---|---|---|
| Child | `child-toy` | Toy-like folder deck |
| 10s | `10s-neon-lab` | Neon side rail and angled glass panels |
| 10s | `10s-pop-sticker` | Bold comic outlines and tilted sticker cards |
| 10s | `10s-pixel-arcade` | Square pixel frames and arcade grid |
| 20s | `20s-midnight-stream` | Wide cinematic hero and overlaid titles |
| 20s | `20s-soft-minimal` | Open spacing and horizontal media rows |
| 20s | `20s-color-block` | Asymmetric color-block bento |
| 30s | `30s-studio-grid` | Aligned studio grid |
| 30s | `30s-calm-gallery` | Spacious staggered arch gallery |
| 30s | `30s-home-cinema` | Wide cinema cards and brass lines |
| 40s | `40s-classic-library` | Sidebar and library-style poster shelves |
| 40s | `40s-forest-view` | Forest header and full-width collection rows |
| 40s | `40s-slate-dashboard` | Compact dashboard modules |
| 50s | `50s-paper-light` | Large type and paper-like two-column view |
| 50s | `50s-navy-theater` | Tall posters and navy theater palette |
| 50s | `50s-warm-lounge` | Arched covers and horizontal video rows |
| 60s | `60s-clear-blue` | Large controls and single-column rows |
| 60s | `60s-warm-comfort` | Spacious rounded cards and warm surfaces |
| 60s | `60s-bold-contrast` | Bold outlines, large type and strong contrast |

## Source structure

```text
src/main/resources/static/themes/
├── catalog.json
├── runtime.js
├── preview.{html,css,js}
├── _shared/base.css
├── child-toy/
│   ├── theme.json
│   ├── theme.css
│   └── assets/{hero,preview}.svg
└── 10s/ ... 60s/              # Each preset has the same file set
```

## Adding a theme

1. Add a preset folder under an existing catalog group, or a safe standalone directory when following the supported directory format. Use a unique lowercase/hyphenated ID and register its relative directory in `catalog.json`.
2. Supply a matching `theme.json` with all required `copy` (Korean) and `copyEn` (English) fields, the exact local hero path and a six-digit theme color. Provide `nameEn` and `descriptionEn` in the catalog entry while preserving its existing ID and Korean fields. Follow an existing manifest; values are rendered as literal text.
3. Add `theme.css`, importing the shared structural foundation with the correct relative path when applicable. Own the composition, palette, density, shape and typography. Preserve navigation semantics and native player behavior.
4. Add self-contained `assets/hero.svg` and `assets/preview.svg`. The latter is a layout illustration for the catalog. Use original or appropriately licensed assets; do not include user video frames, remote scripts, external font services or private media.
5. Keep existing public theme IDs compatible. The 19 bundled presets are the baseline, not a maximum catalog size. Use one of the existing groups unless the preview grouping and validation are explicitly extended together.
6. Run `node scripts/validate-themes.mjs`, build the JAR and run `bash scripts/check.sh`. Inspect mobile/desktop browsing, long titles, focus, reduced motion, notices and native playback. Ensure controls remain usable in a short landscape viewport.

A new preset requires rebuilding the package. Common browsing/cache/queue/player code is not copied into preset folders. Folder-specific names or duration descriptions belong in deployment display-label settings rather than shared theme code. High-contrast presets are design choices, not accessibility certification.
