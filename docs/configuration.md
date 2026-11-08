# Configuration

[한국어](ko/configuration.md) · [Documentation index](../README.md#documentation)

Set environment variables before starting the process or creating the container. Changing them requires a restart or container replacement. This table is the canonical configuration reference; README examples show only the minimum needed to start.

| Variable | JAR default | Meaning and allowed range |
|---|---|---|
| `MOVIESITES_MEDIA_ROOT` | `./movie` | Existing original-media root; use a read-only mount where possible |
| `MOVIESITES_DATA_ROOT` | `./runtime` | Writable generated-data root, separate from media and other instances |
| `MOVIESITES_THEME` | `child-toy` | Exact, case-sensitive registered theme ID; unknown values fail startup |
| `MOVIESITES_FOLDER_LABELS` | `{}` | Optional JSON object mapping a folder name to literal display text |
| `MOVIESITES_BROWSER_CACHE_TTL_SECONDS` | `120` | In-memory directory-response lifetime, 60–180 seconds |
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
