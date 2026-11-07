package io.moviesites;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moviesites")
public record Settings(Path mediaRoot, Path dataRoot, int browserCacheTtlSeconds, int pageSize,
        int maxBrowseSessions, int browseIdleSeconds, int maxStreams, long maxCacheBytes,
        long minFreeBytes, String ffmpeg, String ffprobe) {
    public Settings {
        mediaRoot = mediaRoot.toAbsolutePath().normalize();
        dataRoot = dataRoot.toAbsolutePath().normalize();
        if (mediaRoot.startsWith(dataRoot) || dataRoot.startsWith(mediaRoot)) throw new IllegalArgumentException("Media and data roots must be separate, non-overlapping paths");
        if (browserCacheTtlSeconds < 60 || browserCacheTtlSeconds > 180 || pageSize < 1 || pageSize > 100
                || maxBrowseSessions < 1 || maxBrowseSessions > 32 || browseIdleSeconds < 10 || browseIdleSeconds > 600
                || maxStreams < 1 || maxStreams > 16 || maxCacheBytes < 1048576 || minFreeBytes < 0) throw new IllegalArgumentException("Invalid resource limits");
    }
}
