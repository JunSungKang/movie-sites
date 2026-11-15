package xyz.gg_jskang.moviesites.media.web;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.gg_jskang.moviesites.media.MediaService;
import xyz.gg_jskang.moviesites.media.FolderCoverService;
import xyz.gg_jskang.moviesites.storage.MediaStore;
@RestController
@RequestMapping("/api/media")
public final class MediaController {
    private final MediaService media;
    private final MediaImageCache images;
    public MediaController(MediaStore storage,FolderCoverService covers,MediaService media){this.media=media;this.images=new MediaImageCache(storage,covers,media);}
    @GetMapping("/folder-cover") public ResponseEntity<Resource> folderCover(@RequestParam(defaultValue="")String path,@RequestParam(required=false)String v,@RequestParam(required=false)String w)throws IOException{return images.cover(path,v,w==null?null:Integer.valueOf(w));}
    @GetMapping("/info") public MediaService.Info info(@RequestParam String path,@RequestParam(defaultValue="false")boolean play,@RequestParam(defaultValue="false")boolean retry)throws IOException{return media.inspect(path,play,retry);}
    @GetMapping("/thumbnail") public ResponseEntity<Resource> thumbnail(@RequestParam String path,@RequestParam String v)throws IOException{return images.thumbnail(path,v);}
}
