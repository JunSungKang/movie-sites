package xyz.gg_jskang.moviesites.playback;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.media.MediaService;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.storage.TestStorage;
import xyz.gg_jskang.moviesites.support.ByteRange;
class PlaybackServiceTests {
    @TempDir Path temp;
    TestStorage storage;MediaService media;PlaybackService playback;String version,token;
    @BeforeEach void setup()throws Exception {
        Path probe=temp.resolve("probe.sh");Files.writeString(probe,"#!/bin/sh\nprintf '%s' '{\"format\":{\"duration\":\"5\"},\"streams\":[{\"index\":0,\"codec_type\":\"video\",\"codec_name\":\"h264\",\"pix_fmt\":\"yuv420p\",\"width\":640,\"height\":360}]}'\n");assertTrue(probe.toFile().setExecutable(true));
        Settings settings=new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,1,10_737_418_240L,0,"/bin/false",probe.toString());
        Files.createDirectories(settings.mediaRoot());Files.writeString(settings.mediaRoot().resolve("clip.mp4"),"0123456789");
        storage=new TestStorage(settings);media=new MediaService(settings,storage,new UiLocale("en"),storage.workspace());playback=new PlaybackService(settings,storage,media,storage.workspace());
        MediaService.Info info=null;for(int i=0;i<200;i++){info=media.inspect("clip.mp4",false,false);if(info.state().equals("ready"))break;Thread.sleep(5);}assertNotNull(info);assertEquals("ready",info.state());version=info.version();token=info.streamToken();
    }
    @AfterEach void close()throws Exception{if(media!=null)media.close();if(storage!=null)storage.close();}
    @Test void leaseClosesExactlyOnceAndReleasesItsStreamPermit()throws Exception {
        var lease=playback.open("clip.mp4",version,token);assertThrows(RejectedExecutionException.class,()->playback.open("clip.mp4",version,token));
        var output=new ByteArrayOutputStream();lease.transfer(ByteRange.parse("bytes=3-6",lease.size()),output);assertEquals("3456",output.toString());
        lease.close();lease.close();
        try(var reopened=playback.open("clip.mp4",version,token)){assertEquals(10,reopened.size());assertThrows(RejectedExecutionException.class,()->playback.open("clip.mp4",version,token));}
    }
    @Test void invalidSetupAndDisconnectedOutputDoNotLeakThePermit()throws Exception {
        assertThrows(AccessDeniedException.class,()->playback.open("clip.mp4",version,"invalid"));
        try(var lease=playback.open("clip.mp4",version,token)) {
            assertThrows(IOException.class,()->lease.transfer(ByteRange.parse(null,lease.size()),new OutputStream(){@Override public void write(int value)throws IOException{throw new IOException("disconnected");}}));
        }
        try(var reopened=playback.open("clip.mp4",version,token)){assertEquals(10,reopened.size());}
    }
}
