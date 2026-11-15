package xyz.gg_jskang.moviesites.catalog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import xyz.gg_jskang.moviesites.catalog.internal.DirectoryBrowser;
import xyz.gg_jskang.moviesites.catalog.internal.SortedDirectoryBrowser;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.media.MediaService;
import xyz.gg_jskang.moviesites.metadata.VideoTopics;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.storage.MediaStore;
import xyz.gg_jskang.moviesites.storage.GeneratedDataWorkspace;

@Service
public final class CatalogService implements AutoCloseable {
    public record Entry(String name,String path,String kind,long size,String modified) {}
    public record Listing(String path,String parent,List<Entry> entries,String nextCursor,int warnings) {}
    public record Item(String name,String path,String kind,long size,String modified,Double duration,List<String> tags,String tagSource,String coverVersion) {}
    public record Page(String path,String parent,List<Item> entries,String nextCursor,int warnings,String state,String query,long examined,long analyzed,long unknown,String message) {}
    public record Result(Object body,boolean durationOrder,boolean ready,long cacheSeconds) {}
    private final DirectoryBrowser filesystem;
    private final SortedDirectoryBrowser duration;
    public CatalogService(Settings settings,MediaStore storage,VideoTopics topics,MediaService media,UiLocale locale,GeneratedDataWorkspace workspace) throws IOException {
        filesystem=new DirectoryBrowser(settings,storage);
        duration=new SortedDirectoryBrowser(settings,storage,topics,media::probeDuration,workspace,128,64L*1024*1024,System::nanoTime,locale);
    }
    public CompletableFuture<Result> browse(String path,String cursor,String sort,String query,boolean refresh) {
        if("duration".equals(sort))return duration.browseAsync(path,cursor,query,refresh).thenApply(page->new Result(page,true,"ready".equals(page.state()),duration.cacheSeconds(page)));
        if(!"filesystem".equals(sort))throw new IllegalArgumentException("Unknown list order");
        return filesystem.browseAsync(path,cursor).thenApply(page->new Result(page,false,true,0));
    }
    @Override @PreDestroy public void close() {filesystem.close();duration.close();}
}
