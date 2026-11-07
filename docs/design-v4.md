# MovieSites design v4

Status: implementation scope authorized by the user's request to develop, commit/push and expose the service. The selected interface is the horizontal journal. Kubernetes, Helm, a separate frontend runtime, a database, global indexing and continuous filesystem watches are excluded.

## Architecture

One JDK25/Spring Boot application serves static HTML/CSS/ES modules and JSON/video endpoints. Maven produces one JAR. A multi-stage Dockerfile adds Java and FFmpeg. Videos and all generated data remain in separately configured external directories; container media mounts are read-only. Container runtime paths differ from host paths, connected by bind mounts.

## Filesystem and navigation

The filesystem is the source of truth. Only immediate children of the requested directory are enumerated. No startup tree scan, full media probe or descendant count is performed. Folder breadcrumbs and visited navigation reflect actual queried paths. Directory streams and their short-lived continuation sessions are bounded. Every page has a distinct cursor; replayed, expired or wrong-path cursors are rejected. Iteration is not a frozen sorted snapshot. File mutation during enumeration can change results; refresh starts over.

Only relative paths beneath the configured root are accepted. Secure directory streams and NOFOLLOW operations protect directory traversal and file opens. Symlink entries are skipped. Browser-visible errors do not include server absolute paths. The application cannot identify a moved file's new path without searching, so it reports stale references instead.

## Browser and interface

Horizontal journal rows use a large thumbnail, filename-based title and file metadata. Desktop navigation is compact; mobile stacks the thumbnail above the title. No layout toggle, fabricated descriptions, analytics or external fonts are included. Native dialog/video controls and keyboard focus remain accessible.

A per-tab in-memory LRU caches directory responses for 120 seconds, configurable from 60 to 180. Cache hits do not extend expiry. Expiry does not trigger polling. Explicit refresh bypasses application and HTTP caches. Static HTML references JS/CSS with content-hash query parameters, including the imported cache module; package checks reject stale hashes. Static UI assets are sent with no-store and without timestamp-based 304 responses, because reproducible JAR entry timestamps are not content versions. Cache limits are 20 responses, 2000 list entries and approximately 10 MiB of serialized metadata; this is not a promise about browser decoder/GPU memory. Visible thumbnails are requested with two concurrent enrichment workers. DOM content is bounded by the displayed batch. Cursor history retains at most 20 recent pages; a first-page action restarts longer traversals.

## Playback and work

Media analysis and thumbnail creation happen on demand. Browser-safe MP4 is streamed using bounded buffers and single HTTP byte ranges. Other supported formats are repackaged or transcoded to H.264/yuv420p/AAC MP4, preserving original files. Transcoding produces a complete compatibility file before playback; this initial release is not live HLS transcoding. HDR inputs use SDR tone mapping when supported by the installed FFmpeg filters.

Analysis has two workers and a bounded queue; conversion has one. Per-source jobs are deduplicated and the job map is bounded. Signed playback parameters bind the source path, version and original/converted mode so job-map eviction cannot invalidate an already prepared original stream; signatures are checked before file access and are regenerated after a server restart. Process time, output logs, free space, generated-file size and concurrent streams are controlled. JVM heap is not total container memory because FFmpeg runs separately. Partial files are not published as completed videos. Generated-cache accounting includes in-progress outputs, evicts old unpinned completed files, and retains at most 1024 eviction candidates at a time. Interrupted processing is retryable: startup removes recognized abandoned partial files and process logs from the local data root. An exclusive data-root lock prevents concurrent instances from deleting each other's work; separate instances require separate data roots. Startup does not resume a global media scan.

## Deployment and public access

JAR execution requires installed FFmpeg/ffprobe. The Docker image includes them and runs as a non-root UID. Original media and generated data are excluded from the Docker build context and JAR. The data mount must be writable by the selected UID. Public exposure uses a new independent port without changing existing services. The application is anonymous read-only; only media deliberately placed in the configured root is exposed. Durable availability depends on this host, Docker and external storage remaining running. No custom domain, TLS certificate or cloud hosting account is assumed.

## Traceability and acceptance

The machine-readable specification maps requirements to source artifacts and verification. Unit tests cover path boundaries, symlinks, immediate-only listings, cursor progression/replay, range boundaries and cache TTL/LRU limits. Runtime verification covers actual media processing, byte ranges, public reachability, container limits and package separation. Actual browser/OS coverage and remaining limitations are recorded without representing unrun tests as passing.
