package xyz.gg_jskang.moviesites.playlist;
import java.util.List;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.MediaStore;
import xyz.gg_jskang.moviesites.media.MediaService;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.playlist.internal.RandomPlaylistQueries;
@Service
public final class RandomPlayback implements AutoCloseable {
    public record Item(String name, String path, long size, double duration, String version) {}
    public record Page(String state, String query, String path, int minutes, List<Item> entries,
                       double totalSeconds, long examined, long videos, int checked, int skipped,
                       boolean sampled, boolean limited, String message) {}
    private final RandomPlaylistQueries queries;
    @org.springframework.beans.factory.annotation.Autowired
    public RandomPlayback(Settings settings,MediaStore storage,MediaService media,UiLocale locale){this(settings,storage,media::probeDuration,locale);}
    public RandomPlayback(Settings settings,MediaStore storage,xyz.gg_jskang.moviesites.media.DurationReader durations,UiLocale locale){queries=new RandomPlaylistQueries(settings,storage,durations,new RandomPlaylistQueries.Limits(512,4,250_000,64,128,120_000),locale);}
    public Page request(String path,int minutes,String query,boolean refresh){return queries.request(path,minutes,query,refresh);}
    public void cancel(String path,int minutes,String query){queries.cancel(path,minutes,query);}
    @Override @PreDestroy public void close(){queries.close();}
}
