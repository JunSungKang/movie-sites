# Media library

[한국어](ko/media-library.md) · [Documentation index](../README.md#documentation)

## Folders and titles

The filesystem is the source of truth. Only immediate children of the opened folder are listed; opening a child queries that location. Filesystem enumeration order is used, without a full sort, global search or descendant count. The original file name without its extension is the video title.

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
