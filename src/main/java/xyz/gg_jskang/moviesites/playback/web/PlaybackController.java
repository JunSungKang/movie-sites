package xyz.gg_jskang.moviesites.playback.web;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
import jakarta.servlet.http.HttpServletRequest;
import xyz.gg_jskang.moviesites.playback.PlaybackService;
import xyz.gg_jskang.moviesites.support.ByteRange;
@RestController
@RequestMapping("/api/media")
public final class PlaybackController {
    private final PlaybackService playback;
    public PlaybackController(PlaybackService playback){this.playback=playback;}
    @RequestMapping(value="/stream",method={RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<StreamingResponseBody> stream(@RequestParam String path,@RequestParam String v,@RequestParam String token,@RequestHeader(value="Range",required=false)String header,HttpServletRequest request)throws IOException {
        var lease=playback.open(path,v,token);
        try {
            long size=lease.size();ByteRange range;
            try{range=ByteRange.parse(header,size);}catch(IllegalArgumentException e){lease.close();return ResponseEntity.status(416).header(HttpHeaders.CONTENT_RANGE,"bytes */"+size).build();}
            HttpHeaders headers=new HttpHeaders();headers.set(HttpHeaders.ACCEPT_RANGES,"bytes");headers.setContentType(MediaType.parseMediaType("video/mp4"));headers.setContentLength(range.length());headers.setCacheControl("no-store");
            if(range.partial())headers.set(HttpHeaders.CONTENT_RANGE,"bytes "+range.start()+"-"+range.end()+"/"+size);
            if(request.getMethod().equals("HEAD")||range.length()==0){lease.close();return new ResponseEntity<>(null,headers,range.partial()?HttpStatus.PARTIAL_CONTENT:HttpStatus.OK);}
            WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(lease,new CallableProcessingInterceptor(){
                @Override public <T> void afterCompletion(NativeWebRequest req,java.util.concurrent.Callable<T> task)throws Exception{lease.close();}
            });
            StreamingResponseBody body=output->{try(lease){lease.transfer(range,output);}};
            return new ResponseEntity<>(body,headers,range.partial()?HttpStatus.PARTIAL_CONTENT:HttpStatus.OK);
        }catch(IOException|RuntimeException e){lease.close();throw e;}
    }
}
