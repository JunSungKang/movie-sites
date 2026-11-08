# Getting started

[한국어](ko/getting-started.md) · [Documentation index](../README.md#documentation)

Use the [root README](../README.md) as the documentation index. MovieSites serves a folder-based library from one application; it does not copy your originals into its package.

## Choose a runtime

- **Docker:** install Docker with a Linux container engine. The image includes Java, FFmpeg and ffprobe. See the [Docker guide](docker.md).
- **JAR:** install JDK 25 and `ffmpeg`/`ffprobe` on the server. The committed Gradle Wrapper downloads the pinned build tool. The first build needs access to Gradle distributions, the Gradle Plugin Portal and Maven Central.

Linux and macOS are the native-server targets. Media storage must provide Java `SecureDirectoryStream`; not every mounted filesystem does. For Windows hosts, use Linux containers and verify Docker Desktop path sharing. The presence of `gradlew.bat` provides the standard Windows build launcher, not a promise of native Windows media-storage support.

## Build and run the JAR

```bash
git clone https://github.com/JunSungKang/movie-sites.git
cd movie-sites
./gradlew clean build
mkdir -p movie runtime
MOVIESITES_MEDIA_ROOT="$PWD/movie" \
MOVIESITES_DATA_ROOT="$PWD/runtime" \
java -jar build/libs/moviesites.jar
```

Open [localhost:8080](http://localhost:8080). Original media and writable generated data must be separate, non-overlapping directories. The media directory must already exist. Use absolute paths in production; relative paths are resolved from the process working directory. Run only one instance against each data root.

## Add the first video

Place a video you have the right to serve beneath the media root, for example `movie/Tutorials/Introduction.mp4`. Open the collection and choose the video. The file name without its extension becomes the title. Refresh the displayed list after adding or moving files. An empty library is normal for a fresh clone: only `movie/.gitkeep` is tracked.

H.264/yuv420p video with AAC audio in MP4 can play directly when the supported stream conditions are met. Other decodable inputs may require FFmpeg preparation before playback. The first conversion can take time. See [media management](media-library.md) and [troubleshooting](troubleshooting.md).

## Next steps

Choose a theme and resource limits through the [configuration reference](configuration.md). The current service is anonymous and read-only; anyone who can reach it can browse/play supported media under the chosen root. Configure deployment access before making it public. The [verification report](verification.md) identifies what was actually tested.
