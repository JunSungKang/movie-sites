package xyz.gg_jskang.moviesites.media.internal;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.*;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.MediaStore;
import xyz.gg_jskang.moviesites.media.FolderCoverService;
final class MediaImages {
    private final Settings settings;private final MediaStore storage;private final FolderCoverService validator;
    private final Map<String,Boolean> validatedThumbnails=new LinkedHashMap<>(32,.75f,true);
    MediaImages(Settings settings,MediaStore storage){this.settings=settings;this.storage=storage;validator=new FolderCoverService(storage);}
    // An explicit sidecar travels with its video and is never evicted as generated cache.
    // Its own version must not change the video's stream token or playback cache key.
    public String selectedThumbnailVersion(String path) {
        try {
            String file=storage.normalize(path)+".jpg",version=storage.version(file);
            synchronized(validatedThumbnails){if(validatedThumbnails.get(version)!=null)return version;}
            validator.readImage(file);if(!version.equals(storage.version(file)))return null;
            synchronized(validatedThumbnails){validatedThumbnails.put(version,true);while(validatedThumbnails.size()>256)validatedThumbnails.remove(validatedThumbnails.keySet().iterator().next());}
            return version;
        }
        catch(IOException|IllegalArgumentException e){return null;}
    }
    public Resource thumbnailResource(String path,String version) throws IOException {
        String videoVersion=storage.version(path);
        if(version.equals(selectedThumbnailVersion(path)))return new ByteArrayResource(validator.readImage(path+".jpg"));
        if(!videoVersion.equals(version))throw new NoSuchFileException("Thumbnail version changed");
        Path p=settings.dataRoot().resolve("thumbnails").resolve(version+".jpg");
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException("Thumbnail is not ready");return new FileSystemResource(p);
    }
}
