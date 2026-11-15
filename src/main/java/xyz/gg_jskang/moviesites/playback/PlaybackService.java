package xyz.gg_jskang.moviesites.playback;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.media.MediaService;
import xyz.gg_jskang.moviesites.storage.MediaStore;
import xyz.gg_jskang.moviesites.storage.GeneratedDataWorkspace;
import xyz.gg_jskang.moviesites.support.ByteRange;
@Service
public final class PlaybackService {
    private final Settings settings;private final MediaStore media;private final MediaService preparation;private final GeneratedDataWorkspace workspace;
    private final AtomicInteger streams=new AtomicInteger();
    public PlaybackService(Settings settings,MediaStore media,MediaService preparation,GeneratedDataWorkspace workspace){this.settings=settings;this.media=media;this.preparation=preparation;this.workspace=workspace;}
    public StreamLease open(String path,String version,String token)throws IOException {
        if(!media.version(path).equals(version))throw new IllegalStateException("Video changed; refresh the folder");
        if(streams.incrementAndGet()>settings.maxStreams()){streams.decrementAndGet();throw new RejectedExecutionException();}
        Path prepared=null;SeekableByteChannel channel=null;
        try {
            prepared=preparation.prepared(path,version,token);workspace.pin(prepared);
            channel=prepared==null?media.open(path):Files.newByteChannel(prepared,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);
            return new StreamLease(channel,prepared);
        }catch(IOException|RuntimeException e){try{if(channel!=null)channel.close();}catch(IOException ignored){}finally{workspace.unpin(prepared);streams.decrementAndGet();}throw e;}
    }
    public final class StreamLease implements AutoCloseable {
        private final SeekableByteChannel channel;private final Path pinned;private final AtomicBoolean closed=new AtomicBoolean();
        private StreamLease(SeekableByteChannel channel,Path pinned){this.channel=channel;this.pinned=pinned;}
        public long size()throws IOException{return channel.size();}
        public void transfer(ByteRange range,OutputStream output)throws IOException {
            channel.position(range.start());long remaining=range.length();ByteBuffer buffer=ByteBuffer.allocate(65536);
            while(remaining>0){buffer.clear();buffer.limit((int)Math.min(buffer.capacity(),remaining));int read=channel.read(buffer);if(read<0)break;if(read==0)continue;output.write(buffer.array(),0,read);remaining-=read;}output.flush();
        }
        @Override public void close()throws IOException {if(closed.compareAndSet(false,true))try{channel.close();}finally{workspace.unpin(pinned);streams.decrementAndGet();}}
    }
}
