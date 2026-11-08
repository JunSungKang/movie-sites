package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HttpCacheTests {
    @TempDir Path temp;
    @Test void payloadFreshnessDoesNotSlideAndWeightAndEntryLimitsEvict() throws Exception {
        AtomicLong time=new AtomicLong();AtomicInteger calls=new AtomicInteger();
        var cache=new ByteCache(2,6,2,time::get);
        ByteCache.Loader load=()->{calls.incrementAndGet();return new byte[3];};
        cache.get("a",10,load);time.set(TimeUnit.SECONDS.toNanos(9));assertEquals(1,cache.get("a",10,load).seconds());assertEquals(1,calls.get());
        time.set(TimeUnit.SECONDS.toNanos(10));cache.get("a",10,load);assertEquals(2,calls.get());
        cache.get("b",10,load);cache.get("a",10,load);cache.get("c",10,load);cache.get("b",10,load);assertEquals(5,calls.get());
        AtomicInteger big=new AtomicInteger();for(int i=0;i<2;i++)cache.get("large",10,()->{big.incrementAndGet();return new byte[7];});assertEquals(2,big.get());
    }
    @Test void simultaneousLoadsCoalesceAndDistinctWorkIsBounded() throws Exception {
        var cache=new ByteCache(2,1024,1);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            ByteCache.Loader load=()->{calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}return new byte[]{1};};
            var first=executor.submit(()->cache.get("same",10,load));assertTrue(entered.await(2,TimeUnit.SECONDS));
            var second=executor.submit(()->cache.get("same",10,load));
            assertThrows(RejectedExecutionException.class,()->cache.get("other",10,load));
            release.countDown();assertArrayEquals(first.get(2,TimeUnit.SECONDS).value().bytes(),second.get(2,TimeUnit.SECONDS).value().bytes());assertEquals(1,calls.get());
        }finally{release.countDown();}
    }
    @Test void failuresAreRetriedAndReadsAreBounded() throws Exception {
        var cache=new ByteCache(2,1024,1);AtomicInteger calls=new AtomicInteger();
        for(int i=0;i<2;i++)assertThrows(IOException.class,()->cache.get("bad",10,()->{calls.incrementAndGet();throw new IOException();}));assertEquals(2,calls.get());
        assertThrows(ByteCache.TooLarge.class,()->ByteCache.read(new ByteArrayInputStream(new byte[11]),10));
        assertEquals(10,ByteCache.read(new ByteArrayInputStream(new byte[10]),10).length);
    }
    @Test void versionedCoversArePrivateImmutableConditionalAndChangesUseNewUrls() throws Exception {
        var settings=new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");
        Files.createDirectories(settings.mediaRoot().resolve("Collection"));Path file=settings.mediaRoot().resolve("Collection/.folder.jpg");
        byte[] first=new FolderCoverTests().jpeg(20,10);Files.write(file,first);
        AtomicInteger loads=new AtomicInteger();
        try(var storage=new Storage(settings)){
            var covers=new FolderCoverService(storage){@Override public byte[] read(String folder)throws IOException{loads.incrementAndGet();return super.read(folder);}};
            var mvc=MockMvcBuilders.standaloneSetup(new ApiController(settings,storage,null,null,covers,new PresentationService("child-toy","{}")))
                .addFilters(new WebConfiguration(settings).headers()).setControllerAdvice(new WebConfiguration.Errors()).build();
            String version=storage.version("Collection/.folder.jpg");
            var response=mvc.perform(get("/api/media/folder-cover").param("path","Collection").param("v",version)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","max-age=86400, private, immutable")).andExpect(header().string("X-Robots-Tag","noindex, nofollow, noimageindex")).andReturn();
            String etag=response.getResponse().getHeader("ETag");assertNotNull(etag);
            mvc.perform(get("/api/media/folder-cover").param("path","Collection").param("v",version).header("If-None-Match",etag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));assertEquals(1,loads.get());
            Files.write(file,new FolderCoverTests().jpeg(30,10));String changed=storage.version("Collection/.folder.jpg");assertNotEquals(version,changed);
            mvc.perform(get("/api/media/folder-cover").param("path","Collection").param("v",changed)).andExpect(status().isOk())
                .andExpect(header().string("ETag",org.hamcrest.Matchers.not(etag)));assertEquals(2,loads.get());
            mvc.perform(get("/api/media/folder-cover").param("path","Collection").param("v","outdated"))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
            mvc.perform(get("/api/media/folder-cover").param("path","Collection")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("must-revalidate")));
        }
    }
}
