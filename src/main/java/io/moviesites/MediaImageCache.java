package io.moviesites;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import org.springframework.core.io.*;
import org.springframework.http.*;

final class MediaImageCache {
    private final ByteCache cache=new ByteCache(128,32L*1024*1024,8);
    private final Storage storage;private final FolderCoverService covers;private final MediaService media;
    MediaImageCache(Storage storage,FolderCoverService covers,MediaService media){this.storage=storage;this.covers=covers;this.media=media;}
    ResponseEntity<Resource> cover(String input,String version) throws IOException {
        String path=storage.normalize(input), file=path.isEmpty()?".folder.jpg":path+"/.folder.jpg";
        var value=cache.get("cover\0"+path+"\0"+version,120,()->{
            if(version!=null&&!storage.version(file).equals(version))throw new NoSuchFileException("Cover changed");
            byte[] bytes=covers.read(path);
            if(version!=null&&!storage.version(file).equals(version))throw new NoSuchFileException("Cover changed");
            return bytes;
        });
        return response(value,version!=null);
    }
    ResponseEntity<Resource> thumbnail(String input,String version) throws IOException {
        String path=storage.normalize(input);
        try {
            var value=cache.get("thumbnail\0"+path+"\0"+version,300,()->{
                Resource image=media.thumbnailResource(path,version);byte[] bytes=ByteCache.read(image.getInputStream(),2*1024*1024);
                String file=image instanceof ByteArrayResource?path+".jpg":path;
                if(!storage.version(file).equals(version))throw new NoSuchFileException("Thumbnail changed");return bytes;
            });
            return response(value,true);
        }catch(ByteCache.TooLarge large){return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(60)).cachePrivate().mustRevalidate()).contentType(MediaType.IMAGE_JPEG).body(media.thumbnailResource(path,version));}
    }
    private ResponseEntity<Resource> response(ByteCache.Result value,boolean versioned){
        CacheControl control=CacheControl.maxAge(java.time.Duration.ofSeconds(versioned?86400:value.seconds())).cachePrivate();
        control=versioned?control.immutable():control.mustRevalidate();
        return ResponseEntity.ok().cacheControl(control).eTag(value.value().etag()).contentType(MediaType.IMAGE_JPEG).body(new ByteArrayResource(value.value().bytes()));
    }
}
