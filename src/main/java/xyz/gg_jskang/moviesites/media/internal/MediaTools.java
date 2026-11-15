package xyz.gg_jskang.moviesites.media.internal;
import java.nio.file.Path;
/** Port used by media jobs; process flags and JSON wire formats belong to the FFmpeg adapter. */
interface MediaTools extends AutoCloseable {
    MediaProbe probe(Path input)throws Exception;
    Double duration(Path input)throws Exception;
    void thumbnail(Path input,int videoIndex,Path output)throws Exception;
    void prepare(Path input,MediaProbe probe,PlaybackPlan plan,Path output)throws Exception;
    @Override void close();
}
