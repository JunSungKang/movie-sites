# Architecture

[한국어](ko/architecture.md) · [Documentation index](../README.md#documentation)

MovieSites is one JDK 25/Spring Boot 4.1.2 application serving static HTML/CSS/ES modules, JSON endpoints and video streams. Gradle builds one executable JAR. The filesystem is the media source of truth; original media and writable generated data are separate external roots.

## Browsing and state

Only immediate children of the requested directory are enumerated. The UI uses the duration-sorted query described below; the original streaming API remains available. There is no database, startup tree scan, global index, descendant count or recursive watcher. Default pages contain up to 48 supported entries and each call examines at most 1000 filesystem entries. In the original filesystem-order API, enumeration is not a sorted snapshot; an empty page can still have a continuation cursor.

Up to eight short-lived directory sessions are retained, with idle retirement and ordered, path-bound cursors. Replayed, expired or mismatched cursors fail rather than pretending to be a complete list. Two reader workers and a bounded queue limit concurrent listings; the API times out a delayed browse after eight seconds. A blocked OS file operation is not guaranteed to be immediately canceled by that timeout.

A per-tab in-memory LRU stores up to 20 directory responses, 2000 entries and approximately 10 MiB of serialized metadata. TTL defaults to 120 seconds, does not extend on cache hits and does not trigger polling on expiry. Explicit refresh bypasses the cache. Cursor history retains the most recent 20 pages. Moving files invalidates old references; the server does not search for the new location.

## Storage boundaries

Paths are relative to the configured root. Secure directory streams and NOFOLLOW operations reject traversal and symlink access. The provider must support those operations. Errors do not expose absolute server paths. Data/media roots cannot overlap, and a data-root lock prevents concurrent instances from clearing each other's generated work. The public API has no original-media upload/edit/delete operation.

A fixed `.folder.jpg` provides a folder cover. A video sidecar named with the full video filename plus `.jpg` takes precedence over generated thumbnails. JPEG size/dimension validation uses memory-backed ImageIO input and bounded reads. Explicit images remain external; their version is independent of the original video's stream version.

## Media work and resources

Analysis/thumbnail generation is on demand. Two browser enrichment workers prioritize visible/nearby rows. The server has two analysis workers, one conversion worker, bounded queues and a bounded job map. Compatible H.264/yuv420p/AAC MP4 files are streamed directly; other decodable sources are repackaged or converted before playback, preserving originals and aspect ratio. This is complete-file preparation, not live HLS transcoding.

Single HTTP byte ranges use bounded buffers. Signed playback parameters bind a source path, version and original/converted mode; these signatures protect prepared-stream consistency, not user identity. Keys change on restart. Per-source versioning includes path, size, modification time and file identity, so a special overwrite preserving every version input may require generated-cache cleanup.

Generated-data accounting includes in-progress output, evicts old unpinned files and cleans recognized abandoned work on startup. Process count, queue size, output logs, processing time, free space and concurrent streams are limited. JVM heap is not total container memory; FFmpeg and browser decoders/GPU memory are separate. Actual NAS latency, codec complexity, disk pressure and concurrency need target-environment tests.

## Presentation and playback

The startup theme comes from the packaged allowlist. A theme owns copy, styles and original SVGs; shared browsing/cache/player logic is not duplicated. Preview overrides accept registered IDs only and do not mutate server settings. Folder display labels are optional literal text, independent of paths. Documentation translations do not imply UI localization.

The browser queue is origin/root-scoped localStorage with a 200-entry limit, or an in-memory fallback if storage is unavailable. Play-all snapshots the saved queue, advances on natural completion and closes after the final item. Closing invalidates pending inspection/playback work. The same dialog/video element retains native controls; responsive sizing preserves the original video ratio and keeps controls inside the available viewport.

Static UI responses use `no-store` rather than reproducible archive timestamps as content versions. HTML/module references also carry content hashes. CSP restricts resources to the service's own origin, and focus/reduced-motion rules are kept in the shared presentation.

## HTTP surface

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Application response status, not a full storage audit |
| `GET /api/config` | Root identity, list limits, selected theme and display labels |
| `GET /api/browse?path=...&cursor=...` | Immediate-child continuation page |
| `GET /api/media/folder-cover?path=...` | Exact folder's validated JPEG |
| `GET /api/media/info?path=...&play=true` | Media inspection and optional playback preparation |
| `GET /api/media/thumbnail?path=...&v=...` | Versioned selected/generated thumbnail |
| `GET`/`HEAD /api/media/stream?path=...&v=...&token=...` | Prepared video and single byte-range delivery |

Use values returned by inspection for thumbnail/stream versions and playback tokens. Missing/changed files, expired cursors, processing limits and unavailable storage have distinct error responses. The current access model is anonymous read-only; deployment authentication/TLS are outside this application. See [configuration](configuration.md), [security](../SECURITY.md) and [verification](verification.md).

## Duration-sorted queries

Correct ordering across pages requires the durations of all immediate videos. Two background workers and a two-request waiting queue build short-lived query snapshots only for folders explicitly opened. Two page-reader workers with a four-request queue and an eight-second API timeout isolate filesystem calls from HTTP threads. Opening the directory happens in the background builder. The API returns preparation progress instead of tying up a request until a huge folder finishes. There is no startup scan, recursive index, watcher or database. Filename-topic recommendations and validated optional JSON sidecars are included in each prepared record.

Sort chunks contain at most 128 records. Sorted runs live under the external data root's `work/` directory and merge at most 16 inputs at a time. Each query is capped at 4096 initial runs, 32 KiB per serialized record and 64 MiB of live temporary files, including merge intermediates. These are finite query limits, not a claim that every possible folder fits. Budget/free-space failures return a clear failed preparation rather than a partially sorted success. Query work shares the generated-cache accounting and free-space checks; the global FFmpeg process limit remains three.

At most the configured directory-session count (default eight) is retained. Ready/failed snapshots expire after the normal idle period; an abandoned preparation is retired after 15 idle seconds, with cleanup checks every 15 seconds. Active OS/NAS calls may still outlive cancellation. Temporary files are removed on disposal and recognized abandoned sort files are reclaimed at startup. The data-root lock still prevents multiple instances from sharing work.

Duration results use an LRU of at most 2000 source-version keys. File identity, size and modification changes invalidate reuse; unavailable durations are reused for at most 15 seconds. Source moves and sidecar edits are picked up by a new query. Prepared pages are immutable snapshots; stream access still revalidates the real source. Sorted cursors bind query, path and byte offset with an unexposed per-query secret; replay of a valid cursor reads the same immutable page. Unknown/tampered/expired cursors fail. The original streaming API keeps its previous ordered, single-use cursor contract.

The browser receives at most the configured page size and displays exact duration groups with recommended or explicit topic chips. Pending requests abort on navigation. Polling runs only while a requested query is preparing; ordinary cache expiration does not trigger background polling. Large NAS folders may take substantial time because exact duration order cannot be known without inspecting their immediate videos. These changes bound memory/work but do not provide a huge-folder latency or device-capacity guarantee.
