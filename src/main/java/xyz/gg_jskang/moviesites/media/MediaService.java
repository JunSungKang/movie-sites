package xyz.gg_jskang.moviesites.media;
import java.io.IOException;
import java.nio.file.Path;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.storage.MediaStore;
import xyz.gg_jskang.moviesites.storage.GeneratedDataWorkspace;
import xyz.gg_jskang.moviesites.media.internal.MediaJobs;
/** Public media use cases; process, signing and job mechanics stay inside this feature. */
@Service
public final class MediaService implements AutoCloseable {
    public record Info(String version,String state,String thumbnailState,Double duration,Integer width,Integer height,String codec,String playback,String message,String streamToken,String thumbnailVersion) {}
    private final MediaJobs jobs;
    public MediaService(Settings settings,MediaStore storage,UiLocale locale,GeneratedDataWorkspace workspace){jobs=new MediaJobs(settings,storage,locale,workspace);}
    public Info inspect(String path,boolean play,boolean retry)throws IOException{return jobs.inspect(path,play,retry);}
    public Double probeDuration(String path)throws IOException{return jobs.probeDuration(path);}
    public String selectedThumbnailVersion(String path){return jobs.selectedThumbnailVersion(path);}
    public Resource thumbnailResource(String path,String version)throws IOException{return jobs.thumbnailResource(path,version);}
    public Path prepared(String path,String version,String token)throws IOException{return jobs.prepared(path,version,token);}
    @Override @PreDestroy public void close(){jobs.close();}
}
