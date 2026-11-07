# Theme source directory

This directory owns all presentation presets: their catalog, copy, layout styles, original SVG decoration, layout diagrams and preview page. Shared browsing, caching and video behavior stay outside individual themes.

1. Add a preset directory under its catalog group, or a standalone directory for a general preset. Copy the shape of an existing `theme.json`, `theme.css` and `assets/hero.svg` and `assets/preview.svg`.
2. Register a unique lowercase ID and a safe relative directory in `catalog.json`. Server startup and browser preview both use this allowlist. Do not accept arbitrary paths or external style URLs.
3. Supply every required copy field. Text is rendered with `textContent`. Keep collection-specific names and duration descriptions out of shared theme copy; deployments can use `MOVIESITES_FOLDER_LABELS`.
4. Import `_shared/base.css` when using the common structural foundation. Set all palette tokens and add the composition rules in the preset stylesheet. Distinguish layout, density, shape and type instead of only recoloring. The preserved `child-toy` stylesheet is self-contained.
5. Use self-contained SVG decoration and system fonts. Keep media files, collection covers, credentials and runtime data outside this tree. Check the rights of any new asset and update third-party notices when applicable.
6. Update catalog expectations when the agreed preset inventory changes. Run the theme validator, application build and package checks, then inspect real mobile/desktop navigation and native video playback. See the repository development guide.

`MOVIESITES_THEME` is read at process startup. A new theme requires rebuilding the package; choosing an already packaged theme requires only restarting with its ID. `/themes/preview.html` provides read-only previews without changing that setting. The age groups are optional style categories, never audience restrictions or inferred user ages.
