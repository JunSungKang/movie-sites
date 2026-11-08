# Configuration

[한국어](ko/configuration.md) · [Documentation index](../README.md#documentation)

Set environment variables before starting the process or creating the container. Changing them requires a restart or container replacement. This table is the canonical configuration reference; README examples show only the minimum needed to start.

| Variable | JAR default | Meaning and allowed range |
|---|---|---|
| `MOVIESITES_MEDIA_ROOT` | `./movie` | Existing original-media root; use a read-only mount where possible |
| `MOVIESITES_DATA_ROOT` | `./runtime` | Writable generated-data root, separate from media and other instances |
| `MOVIESITES_PUBLIC_URL` | blank | Optional public HTTP(S) origin (e.g. `https://media.example.org`), without credentials, path, query or fragment |
| `MOVIESITES_THEME` | `child-toy` | Exact, case-sensitive registered theme ID; unknown values fail startup |
| `MOVIESITES_FOLDER_LABELS` | `{}` | Optional JSON object mapping a folder name to literal display text |
| `MOVIESITES_BROWSER_CACHE_TTL_SECONDS` | `120` | Server snapshot and maximum browser directory-cache freshness, 60–180 seconds |
| `MOVIESITES_PAGE_SIZE` | `48` | Entries returned per page, 1–100 |
| `MOVIESITES_MAX_STREAMS` | `4` | Concurrent video transmissions, 1–16 |
| `MOVIESITES_MAX_CACHE_BYTES` | `10737418240` | Generated-data budget in bytes, at least 1048576; default 10 GiB |
| `MOVIESITES_MIN_FREE_BYTES` | `536870912` | Minimum free bytes for new processing, zero or greater |
| `MOVIESITES_FFMPEG` | `ffmpeg` | FFmpeg executable or executable path |
| `MOVIESITES_FFPROBE` | `ffprobe` | ffprobe executable or executable path |
| `PORT` | `8080` | HTTP listening port; container publish mapping must match it |

The Docker image defaults to `/media` and `/data` for its two roots. These are container paths; the host mount sources can be elsewhere. [`.env.example`](../.env.example) contains public Docker values. Pass it with `--env-file`; the JAR does not automatically load `.env` files. Host JAR execution should export host paths rather than source a container-path example.

## Display labels

```bash
export MOVIESITES_FOLDER_LABELS='{"Tutorials":"Learning","Interviews":"Conversations"}'
```

Labels affect presentation only. They do not rename files, change API paths or calculate durations. The same folder name receives the same label at any depth. The JSON string is limited to 8192 characters and 32 entries; names and values must be non-empty strings up to 255 characters, without control characters, and names cannot contain path separators. Oversized or malformed settings fail startup. If a label describes a duration or category, the operator is responsible for keeping that wording accurate.

## Resource behavior

The application also uses fixed bounds for directory sessions, reader/analysis queues and conversion workers. See [architecture](architecture.md). A cache-byte budget is not a total memory limit. FFmpeg, the JVM, browser decoders and GPU memory have different costs. Use container/host limits and test your workload; the documented defaults are not a capacity guarantee.

The introduction and library use the same `MOVIESITES_THEME`: shared logo/header/footer, packaged theme stylesheet and illustration. The introduction resolves these at startup and renders visible HTML without JavaScript or catalog requests. Changing the runtime theme and restarting updates both pages. `css/about.css` owns introduction layout only; visual tokens and assets remain in the registered theme directory.

## Search visibility

The public introduction is `/about`. It has server-rendered generic content, description, Open Graph/Twitter metadata and WebPage JSON-LD; it never loads the media catalog. `MOVIESITES_PUBLIC_URL` supplies its canonical, Open Graph URL and `/sitemap.xml` entries. Use the externally reachable origin, including a non-default port when needed. Request `Host`/forwarded headers never determine these links. Invalid settings fail startup; a blank value omits canonical URLs and makes the sitemap return 503 until configured. Restart after changing the setting.

The application at `/`, catalog query URLs, APIs, image responses and video responses carry `X-Robots-Tag: noindex, nofollow, noimageindex`; the application HTML also has a robots meta tag. The sitemap lists only `/about`. `robots.txt` permits crawling so compliant search engines can read those exclusions. This requests search exclusion, not authentication or guaranteed deletion of previously indexed pages. A reachable anonymous service still exposes its media to visitors. See Google's [noindex guidance](https://developers.google.com/search/docs/crawling-indexing/block-indexing).

## Cache policy

| Data | Server behavior | HTTP/browser behavior |
|---|---|---|
| Duration-sorted ready pages | Reuse one current-folder snapshot for the configured 60–180 seconds; coalesce preparing queries | `private`, ETag, remaining snapshot freshness and `must-revalidate` |
| Preparing/failed queries, raw filesystem cursors, configuration, media info and playback | Never treated as reusable list pages | `no-store`; playback tokens are not persisted in the list cache |
| Folder/video JPEGs | Shared bounded payload LRU: 128 entries / 32 MiB / 8 in-flight loads; fixed 120-second covers / 300-second thumbnail freshness | Validated version URLs: `private, max-age=86400, immutable`; unversioned covers: remaining server freshness and `must-revalidate` |
| Packaged JS/CSS/JSON/SVG/PNG | Payload LRU: 256 entries / 8 MiB / 32 in-flight loads; at most 1 MiB per resource | A `v` matching the actual SHA-256 fingerprint gets `public, max-age=31536000, immutable`; missing/incorrect fingerprints revalidate with ETag and `no-cache` |
| Application/preview HTML | Served from the current package | `no-store`, with archive timestamps disabled |
| Public introduction / sitemap | Startup-rendered content, no catalog access | Public five-minute freshness; ETag on introduction/sitemap |

Generated thumbnails larger than the 2 MiB in-memory read limit stream from disk with a private 60-second revalidation policy instead of entering the payload cache.

The per-tab memory cache is limited to 20 pages, 2000 entries and approximately 10 MiB. Optional `sessionStorage` restoration uses one bounded record (about 2.8 MiB serialized budget), retains the original expiry and discards records from another root/server instance. Storage denial/quota errors fall back to memory. It stores list metadata including titles/paths on that device, not videos or playback authorization. Clear site storage to remove it.

The refresh button invalidates that folder's browser pages and sends `refresh=true` for a fresh server snapshot. Cache expiry is fixed, does not slide on hits, and does not start background polling. A refreshed listing uses current folder-cover versions; image versions use the existing file-identity/size/mtime contract. Old version URLs can retain old image bytes until cache expiry. Text/JSON/SVG responses above 1024 bytes can be gzip-compressed when negotiated; video and JPEG compression is excluded. ETags let unchanged responses return 304 without retransmitting their bodies. See [MDN Cache-Control](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Cache-Control).
