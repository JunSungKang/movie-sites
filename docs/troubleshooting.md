# Troubleshooting

[한국어](ko/troubleshooting.md) · [Documentation index](../README.md#documentation)

## The library is empty

A fresh checkout contains only `movie/.gitkeep`. Check the configured media root and open the correct folder. Only immediate children and supported candidate extensions are listed. A cached list can be stale until refresh. Storage failures should produce an error rather than being interpreted as a successful empty listing.

## The container cannot write, or the root does not exist

Create both bind-mount directories before startup. Confirm the container user can read media and write data, and Docker Desktop shares the chosen host paths. Inside the container, settings must reference `/media` and `/data`, not the host source paths. Keep media/data roots non-overlapping.

## Data root is already in use

Another instance owns the data-root lock. Stop that instance or choose a different data root and port. Do not delete a lock file to make two active processes share the same generated-data directory.

## Secure directory access is unavailable

The selected provider/mount does not support the required Java secure directory stream operations. Verify the actual filesystem. Use a supported Linux/macOS server or Linux container and suitable storage. A Windows build launcher does not guarantee native Windows storage compatibility. See [getting started](getting-started.md).

## A thumbnail or folder cover does not appear

Use the exact `.folder.jpg` or `video.mp4.jpg` naming described in [media management](media-library.md). Verify JPEG encoding, file size/dimensions, read permissions and absence of symlinks. Invalid/missing video sidecars use generated thumbnails when media inspection succeeds. Refresh after changes.

## A video is waiting or fails to play

Check FFmpeg/ffprobe availability, source integrity, codecs, free space and generated-cache budget. Conversion is queued one at a time and completes before playback. Listed extensions are not codec certification. Use the retry action after correcting the problem. Autoplay denial requires a user action; it is not proof that the file is damaged.

## The list or a queued item points to a moved file

Refresh the list and re-add the new path to the queue. The application does not scan the whole storage tree to resolve moved paths. Expired continuation cursors require restarting the list. Queue persistence is limited to the current browser origin and media-root identity; a different host/port/browser is a different storage context.

## A theme or label setting prevents startup

Use an exact ID from the [theme list](themes.md). Validate the folder-label JSON and its bounds in [configuration](configuration.md). Changing a process environment or an env file does not update an existing container; recreate it with the intended values.

## Build tool or dependency downloads fail

Use JDK 25 and the committed `gradlew`/`gradlew.bat`. Check network/proxy/CA access to the configured HTTPS distribution and repositories. Do not disable certificate or checksum validation. A separate Gradle install is unnecessary. Build with `./gradlew clean build`, then run the [repository checks](development.md).

## Reporting a bug

Include the exact commit, Java/Gradle or image version, OS, filesystem/mount type, browser, safe reproduction and relevant redacted logs. `/api/health` reports application response status, not NAS-wide health. Do not attach private videos, tokens or personal paths to a public issue. Use the [issue template](https://github.com/JunSungKang/movie-sites/issues/new/choose), or follow the [security policy](../SECURITY.md) for sensitive reports.
