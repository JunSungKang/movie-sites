package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SortedDirectoryTests {
    @TempDir Path temp;
    Storage storage; Settings settings; VideoTopics topics;
    @BeforeEach void setup() throws Exception {
        settings=new Settings(temp.resolve("media"),temp.resolve("data"),120,3,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");
        Files.createDirectories(settings.mediaRoot());storage=new Storage(settings);topics=new VideoTopics(storage);
    }
    @AfterEach void close() throws Exception {storage.close();}
    void video(String name) throws Exception {Files.writeString(settings.mediaRoot().resolve(name),"video");}
    SortedDirectoryBrowser.Page ready(SortedDirectoryBrowser browser,String path) throws Exception {
        var page=browser.browse(path,null,null);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(page.state().equals("preparing")&&System.nanoTime()<deadline){Thread.sleep(5);page=browser.browse(path,null,page.query());}
        assertEquals("ready",page.state(),page.message());return page;
    }
    List<SortedDirectoryBrowser.Item> all(SortedDirectoryBrowser browser,String path) throws Exception {
        var page=ready(browser,path);List<SortedDirectoryBrowser.Item> entries=new ArrayList<>(page.entries());
        while(page.nextCursor()!=null){page=browser.browse(path,page.nextCursor(),null);entries.addAll(page.entries());}
        return entries;
    }
    long temporaryRuns() throws Exception {try(var files=Files.list(settings.dataRoot().resolve("work"))){return files.filter(p->p.getFileName().toString().startsWith("browse-")).count();}}
    @Test void ordersAllImmediateVideosAcrossPagesAndMergePassesWithUnknownsLast() throws Exception {
        for(int i=0;i<83;i++)video("clip-"+i+".mp4");video("broken.mp4");Files.createDirectories(settings.mediaRoot().resolve("Collection/deep"));Files.writeString(settings.mediaRoot().resolve("Collection/deep/hidden.mp4"),"hidden");
        AtomicInteger calls=new AtomicInteger();
        try(var browser=new ClosingBrowser(new SortedDirectoryBrowser(settings,storage,topics,path->{calls.incrementAndGet();assertFalse(path.contains("/"));return path.equals("broken.mp4")?null:Double.valueOf(83-Integer.parseInt(path.substring(5,path.indexOf('.'))));},null,2))){
            var entries=all(browser.value,"");assertEquals(85,entries.size());assertEquals("folder",entries.getFirst().kind());assertEquals("broken.mp4",entries.getLast().path());
            var lengths=entries.stream().filter(e->e.duration()!=null).map(SortedDirectoryBrowser.Item::duration).toList();assertEquals(lengths.stream().sorted().toList(),lengths);assertEquals(84,calls.get());
            assertTrue(temporaryRuns()>0);
        }
        assertEquals(0,temporaryRuns());
    }
    @Test void cursorIsBoundToQueryPathAndSignedOffsetAndCanReplayImmutablePages() throws Exception {
        for(int i=0;i<8;i++)video("video-"+i+".mp4");Files.createDirectory(settings.mediaRoot().resolve("other"));
        try(var browser=new ClosingBrowser(new SortedDirectoryBrowser(settings,storage,topics,path->60.0,null,2))){
            var first=ready(browser.value,"");String cursor=first.nextCursor();assertNotNull(cursor);
            assertEquals(browser.value.browse("",cursor,null).entries(),browser.value.browse("",cursor,null).entries());
            assertThrows(DirectoryBrowser.ExpiredCursor.class,()->browser.value.browse("other",cursor,null));
            assertThrows(DirectoryBrowser.ExpiredCursor.class,()->browser.value.browse("",cursor.replaceFirst("\\.[0-9]+\\.",".1."),null));
            assertThrows(IllegalArgumentException.class,()->browser.value.browse("../outside",null,null));
        }
    }
    @Test void refreshRevalidatesFileVersionsAndTopicOverridesWithoutWatchingOrDatabase() throws Exception {
        video("야채.mp4");AtomicInteger calls=new AtomicInteger();
        try(var browser=new ClosingBrowser(new SortedDirectoryBrowser(settings,storage,topics,path->{calls.incrementAndGet();return (double)Files.size(settings.mediaRoot().resolve(path));},null,2))){
            var first=all(browser.value,"").getFirst();assertEquals(5.0,first.duration());assertEquals("filename",first.tagSource());
            all(browser.value,"");assertEquals(1,calls.get());
            Files.writeString(settings.mediaRoot().resolve("야채.mp4"),"changed video");Files.writeString(settings.mediaRoot().resolve("야채.mp4.json"),"{\"tags\":[\"Manual topic\"]}");
            var changed=all(browser.value,"").getFirst();assertEquals(13.0,changed.duration());assertEquals(List.of("Manual topic"),changed.tags());assertEquals(2,calls.get());
        }
    }
    @Test void preparationIsObservableAndWorkerQueueIsBoundedAndClosingRemovesRuns() throws Exception {
        video("clip.mp4");CountDownLatch gate=new CountDownLatch(1);
        var browser=new SortedDirectoryBrowser(settings,storage,topics,path->{try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}return 60.0;},null,2);
        try {
            for(int i=0;i<4;i++){var page=browser.browse("",null,null);assertEquals("preparing",page.state());assertEquals("preparing",browser.browse("",null,page.query()).state());}
            assertThrows(RejectedExecutionException.class,()->browser.browse("",null,null));
        }finally{browser.close();gate.countDown();}
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(temporaryRuns()!=0&&System.nanoTime()<deadline)Thread.sleep(10);assertEquals(0,temporaryRuns());
    }
    @Test void diskBudgetFailureIsExplicitAndCleansPartialResultsInsteadOfShowingAPartialSort() throws Exception {
        for(int i=0;i<8;i++)video("clip-"+i+".mp4");
        try(var browser=new ClosingBrowser(new SortedDirectoryBrowser(settings,storage,topics,path->60.0,null,2,100))){
            var page=browser.value.browse("",null,null);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(page.state().equals("preparing")&&System.nanoTime()<deadline){Thread.sleep(5);page=browser.value.browse("",null,page.query());}
            assertEquals("failed",page.state());assertTrue(page.entries().isEmpty());assertFalse(page.message().contains(temp.toString()));
        }
        assertEquals(0,temporaryRuns());
    }
    @Test void busyProcessingDoesNotMisclassifyVideosAsUnknownDurations() throws Exception {
        video("clip.mp4");
        try(var browser=new ClosingBrowser(new SortedDirectoryBrowser(settings,storage,topics,path->{throw new RejectedExecutionException();},null,2))){
            var page=browser.value.browse("",null,null);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(page.state().equals("preparing")&&System.nanoTime()<deadline){Thread.sleep(5);page=browser.value.browse("",null,page.query());}
            assertEquals("failed",page.state());assertTrue(page.entries().isEmpty());assertEquals(0,page.unknown());
        }
    }
    @Test void slowStorageDoesNotBlockInitialPreparationResponseOrTheHttpThread() throws Exception {
        video("clip.mp4");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var slowSettings=new Settings(settings.mediaRoot(),temp.resolve("slow-data"),120,3,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");
        Storage delayed=new Storage(slowSettings){
            @Override public SecureDirectoryStream<Path> directory(String input) throws IOException {
                entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException();}
                return super.directory(input);
            }
        };
        var browser=new SortedDirectoryBrowser(slowSettings,delayed,new VideoTopics(delayed),path->60.0,null,2);
        try {
            var page=browser.browseAsync("",null,null).get(2,TimeUnit.SECONDS);assertEquals("preparing",page.state());assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals("preparing",browser.browseAsync("",null,page.query()).get(2,TimeUnit.SECONDS).state());
        }finally{release.countDown();browser.close();delayed.close();}
    }
    @Test void durationProbesShareAnalysisCapacityInsteadOfCausingPlaybackAnalysisToFail() throws Exception {
        for(String name:List.of("a.mp4","b.mp4","c.mp4","d.mp4"))video(name);
        Path script=temp.resolve("probe.sh");
        Files.writeString(script,"#!/bin/sh\nfor arg do last=\"$arg\"; done\nprintf x > \"$last.started\"\nsleep 1\nprintf '%s' '{\"format\":{\"duration\":\"5\"},\"streams\":[{\"index\":0,\"codec_type\":\"video\",\"codec_name\":\"h264\",\"pix_fmt\":\"yuv420p\",\"width\":64,\"height\":36}]}'\n");
        assertTrue(script.toFile().setExecutable(true));
        var probeSettings=new Settings(settings.mediaRoot(),settings.dataRoot(),120,3,8,120,4,10_737_418_240L,0,"/bin/false",script.toString());
        var media=new MediaService(probeSettings,storage);var callers=Executors.newFixedThreadPool(2);
        try {
            var a=callers.submit(()->media.probeDuration("a.mp4"));var b=callers.submit(()->media.probeDuration("b.mp4"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while((!Files.exists(settings.mediaRoot().resolve("a.mp4.started"))||!Files.exists(settings.mediaRoot().resolve("b.mp4.started")))&&System.nanoTime()<deadline)Thread.sleep(5);
            assertTrue(Files.exists(settings.mediaRoot().resolve("b.mp4.started")));
            media.inspect("c.mp4",false,false);media.inspect("d.mp4",false,false);
            assertEquals(5.0,a.get(5,TimeUnit.SECONDS));assertEquals(5.0,b.get(5,TimeUnit.SECONDS));
            MediaService.Info c,d;
            do {c=media.inspect("c.mp4",false,false);d=media.inspect("d.mp4",false,false);if(c.state().equals("ready")&&d.state().equals("ready"))break;Thread.sleep(10);}while(System.nanoTime()<deadline);
            assertEquals("ready",c.state());assertEquals("ready",d.state());
        }finally{callers.shutdownNow();media.close();}
    }
    static class ClosingBrowser implements AutoCloseable { final SortedDirectoryBrowser value;ClosingBrowser(SortedDirectoryBrowser value){this.value=value;}public void close(){value.close();} }
}
