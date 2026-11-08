# Media library

[한국어](ko/media-library.md) · [Documentation index](../README.md#documentation)

## Folders and titles

The filesystem is the source of truth. Only immediate children of the opened folder are listed; opening a child queries that location. The interface orders every immediate video by actual duration, shortest first, across continuation pages. Folders come first; it does not search descendants or offer global search/counts. The original file name without its extension is the video title.

```text
videos/
├── Travel/
│   ├── .folder.jpg
│   ├── A day by the sea.mp4
│   └── A day by the sea.mp4.jpg
└── Tutorials/
    └── Introduction.mp4
```

Names and paths may contain non-ASCII characters. Relative paths stay beneath the configured media root; symbolic links are not followed. Use the visible home/return actions to navigate. After moving, adding or deleting files, refresh the list. The application does not search for a moved file's new location.

## Images

- **Folder cover:** place `.folder.jpg` in the exact folder. No descendant search is performed. A missing cover uses the default drawing.
- **Video thumbnail:** append `.jpg` to the complete video file name, for example `Introduction.mp4.jpg`. An absent or invalid sidecar falls back to an automatically generated thumbnail when processing succeeds.

Use JPEGs up to 512 KiB, with each dimension at most 1280 pixels and no more than 921600 total pixels. A 960×540 folder cover or 640×360 video image fits these limits. Keep important content visible when a theme crops a thumbnail. The actual player preserves the video's aspect ratio. Explicit images stay beside the originals, outside Git/JAR/container images, and their version is independent of the video's stream version. Use images you have permission to serve.

## Formats and playback

Common candidate extensions include MP4, MOV, MKV, WebM and AVI; the full extension set is declared in [DirectoryBrowser.java](../src/main/java/io/moviesites/DirectoryBrowser.java). An extension being listed does not guarantee a decodable container/codec combination.

Compatible MP4/H.264/yuv420p/AAC inputs with the supported stream arrangement use the original file. Other supported inputs are repackaged or converted to a complete H.264/AAC playback file before streaming. Conversion preserves originals and keeps the image within 1920×1080 while preserving aspect ratio. Available FFmpeg filters determine HDR-to-SDR handling. Damaged, encrypted or unsupported inputs can fail with a recoverable error.

Native HTML video controls provide playback, sound and supported browser actions. Decoder and autoplay/fullscreen behavior vary by browser/device; see [verification](verification.md).

## Playback queue

Keep one queue in the current browser, add individual videos or the current displayed page, reorder/remove entries and clear it. Duplicate paths are stored once, with a maximum of 200 entries. The queue is stored in `localStorage` for the current origin and media-root identity; it is not an account-backed server playlist and is not shared across devices or different service addresses.

If storage is blocked or full, the current tab can continue with an in-memory queue and an explanatory notice. Play-all takes a snapshot from the first item; edits made during playback apply to the next play-all session. Natural completion advances through the snapshot and closes the popup after the final video, leaving the selection page and saved queue intact. Manual close cancels pending playback work. Missing media and autoplay denial provide explicit recovery actions rather than silently claiming successful playback.

## Caching and large storage

Directory-response caching expires from fetch time and is refreshed on demand, not by background polling. It is bounded by response count, item count and approximate serialized size. Long listings use continuation pages. Filesystem changes can invalidate a cached selection or cursor; refresh to restart. NAS availability, OS-level file-call delays and media processing remain deployment concerns described in [architecture](architecture.md).

## Duration order and topic tags

The browser requests `GET /api/browse?sort=duration&path=...`. A new directory query may initially return `state: preparing` with a query ID and progress; poll that same ID until ready, then use its signed continuation cursors. The existing API without `sort=duration` retains filesystem-order streaming for compatibility. The UI shows preparation progress, and only ready pages enter its normal 60–180-second cache. The server reuses preparing/ready snapshots until fixed expiry. Request `refresh=true` on the initial query (not each preparation poll) to bypass reuse. Sorting is a current-folder snapshot, not a filesystem watcher; refresh after file moves or metadata changes. The UI refresh button performs this bypass.

Videos are separated at exact, unrounded thresholds: **under 5 minutes**, **5 to under 10 minutes**, **10 to under 20 minutes**, and **20 minutes or longer**. Exactly 5, 10 and 20 minutes start the next group. Unreadable/non-positive/non-finite durations appear in a separate group at the end; they are never treated as zero-length videos. Dividers repeat on a new page when that page continues a group. Processing saturation fails the query explicitly rather than labelling all busy videos as unknown.

Initial ordering must inspect all immediate video durations before the first correctly sorted page can be returned. It can take time on large or slow directories. Only duration probing runs at this stage; thumbnails are prepared for visible rows later. It does not recursively inspect another folder. See [resource bounds](architecture.md#duration-sorted-queries) before using very large NAS folders.

Topic chips appear below each title. Filename keyword matches are explicitly marked as **recommended topics**, not verified recognition of the video content. Examples include manners, food habits, toilet training, play, social skills, safety and health; unmatched titles show an unspecified topic. Suggested labels currently follow the Korean UI. Any collection can supply its own labels using an external UTF-8 sidecar named after the full video filename:

```text
Tutorial.mp4
Tutorial.mp4.json
```

```json
{"tags": ["Learning", "Practice"]}
```

The `tags` array takes precedence over recommendations. An explicit empty array disables suggestions for that video. Use at most 8 labels, each 1–40 characters without control characters, in a JSON file no larger than 8192 bytes. Duplicates are removed. Invalid/oversized/unreadable metadata falls back to recommendations; symlinks are not followed. Labels are literal text. Sidecars remain external and untracked alongside original media, like covers; the app never writes them or modifies videos. Edit on the host/storage side and refresh the collection to apply changes. A read-only container media mount still permits the app to read these files.
