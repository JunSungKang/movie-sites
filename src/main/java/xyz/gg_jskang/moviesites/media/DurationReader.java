package xyz.gg_jskang.moviesites.media;
import java.io.IOException;
import xyz.gg_jskang.moviesites.storage.MediaStore;
import xyz.gg_jskang.moviesites.media.internal.CachingDurationReader;
@FunctionalInterface
public interface DurationReader {
    Double read(String path) throws IOException;
    static DurationReader cached(MediaStore storage,DurationReader source) {return new CachingDurationReader(storage,source);}
}
