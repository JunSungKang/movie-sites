package io.moviesites;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Settings settings;private final Storage storage;private final DirectoryBrowser browser;private final MediaService media;private final FolderCoverService covers;private final PresentationService presentation;
    private SortedDirectoryBrowser sorted;
    private final MediaImageCache images;
    private final String instanceId=java.util.UUID.randomUUID().toString();
    @org.springframework.beans.factory.annotation.Autowired
    public void setSortedDirectoryBrowser(SortedDirectoryBrowser sorted) { this.sorted = sorted; }
    public ApiController(Settings settings,Storage storage,DirectoryBrowser browser,MediaService media,FolderCoverService covers,PresentationService presentation){this.settings=settings;this.storage=storage;this.browser=browser;this.media=media;this.covers=covers;this.presentation=presentation;this.images=new MediaImageCache(storage,covers,media);}
    @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","application","MovieSites");}
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("rootId",storage.rootId(),"instanceId",instanceId,"cacheTtlSeconds",settings.browserCacheTtlSeconds(),"pageSize",settings.pageSize(),"sourceUrl","https://github.com/JunSungKang/movie-sites","license","AGPL-3.0-only","theme",presentation.theme(),"folderLabels",presentation.folderLabels());}
    @GetMapping("/browse") public CompletableFuture<?> browse(@RequestParam(defaultValue="")String path,@RequestParam(required=false)String cursor,
            @RequestParam(defaultValue="filesystem")String sort,@RequestParam(required=false)String query,@RequestParam(defaultValue="false")boolean refresh) throws IOException {
        if (sort.equals("duration")) return sorted.browseAsync(path,cursor,query,refresh).thenApply(page->{
            if(!page.state().equals("ready"))return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
            String tag="W/\"list-"+Storage.digest(page.toString())+"\"";
            return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(sorted.cacheSeconds(page))).cachePrivate().mustRevalidate()).eTag(tag).body(page);
        });
        if (!sort.equals("filesystem")) throw new IllegalArgumentException("Unknown list order");
        return browser.browseAsync(path,cursor);
    }
    @GetMapping("/media/folder-cover") public ResponseEntity<Resource> folderCover(@RequestParam(defaultValue="")String path,@RequestParam(required=false)String v)throws IOException{
        return images.cover(path,v);
    }
    @GetMapping("/media/info") public MediaService.Info info(@RequestParam String path,@RequestParam(defaultValue="false")boolean play,@RequestParam(defaultValue="false")boolean retry)throws IOException{return media.inspect(path,play,retry);}
    @GetMapping("/media/thumbnail") public ResponseEntity<Resource> thumbnail(@RequestParam String path,@RequestParam String v)throws IOException{
        return images.thumbnail(path,v);
    }
    @RequestMapping(value="/media/stream",method={RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<StreamingResponseBody> stream(@RequestParam String path,@RequestParam String v,@RequestParam String token,@RequestHeader(value="Range",required=false)String header,HttpServletRequest request)throws IOException{
        if(!storage.version(path).equals(v))throw new IllegalStateException("Video changed; refresh the folder");
        if(!media.acquireStream())throw new RejectedExecutionException();
        SeekableByteChannel channel=null;Path prepared=null;
        try{
            prepared=media.prepared(path,v,token);media.pin(prepared);
            channel=prepared==null?storage.open(path):Files.newByteChannel(prepared,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
            long size=channel.size();ByteRange range;
            try{range=ByteRange.parse(header,size);}catch(IllegalArgumentException e){channel.close();media.unpin(prepared);media.releaseStream();return ResponseEntity.status(416).header(HttpHeaders.CONTENT_RANGE,"bytes */"+size).build();}
            HttpHeaders headers=new HttpHeaders();headers.set(HttpHeaders.ACCEPT_RANGES,"bytes");headers.setContentType(MediaType.parseMediaType("video/mp4"));headers.setContentLength(range.length());headers.setCacheControl("no-store");
            if(range.partial())headers.set(HttpHeaders.CONTENT_RANGE,"bytes "+range.start()+"-"+range.end()+"/"+size);
            if(request.getMethod().equals("HEAD")||range.length()==0){channel.close();media.unpin(prepared);media.releaseStream();return new ResponseEntity<>(null,headers,range.partial()?HttpStatus.PARTIAL_CONTENT:HttpStatus.OK);}
            SeekableByteChannel source=channel;Path pinned=prepared;
            StreamingResponseBody body=output->{try(source){source.position(range.start());long remaining=range.length();ByteBuffer buffer=ByteBuffer.allocate(65536);while(remaining>0){buffer.clear();buffer.limit((int)Math.min(buffer.capacity(),remaining));int read=source.read(buffer);if(read<0)break;if(read==0)continue;output.write(buffer.array(),0,read);remaining-=read;}output.flush();}finally{media.unpin(pinned);media.releaseStream();}};
            return new ResponseEntity<>(body,headers,range.partial()?HttpStatus.PARTIAL_CONTENT:HttpStatus.OK);
        }catch(IOException|RuntimeException e){if(channel!=null)try{channel.close();}catch(IOException ignored){}media.unpin(prepared);media.releaseStream();throw e;}
    }
}
