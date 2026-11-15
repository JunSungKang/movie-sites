package xyz.gg_jskang.moviesites.media.internal;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.TestStorage;
class CachingDurationReaderTests {
    @TempDir Path temp;
    TestStorage storage;
    @BeforeEach void setup()throws Exception{var settings=new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");Files.createDirectories(settings.mediaRoot());for(String file:new String[]{"a.mp4","b.mp4","c.mp4"})Files.writeString(settings.mediaRoot().resolve(file),"original");storage=new TestStorage(settings);}
    @AfterEach void close()throws Exception{storage.close();}
    @Test void validatesSourceVersionsOnHitsAndAfterLoading()throws Exception {
        var calls=new AtomicInteger();var path=temp.resolve("media/a.mp4");
        var cache=new CachingDurationReader(storage,p->{calls.incrementAndGet();return (double)Files.size(path);});
        assertEquals(8.0,cache.read("a.mp4"));assertEquals(8.0,cache.read("a.mp4"));assertEquals(1,calls.get());
        Files.writeString(path,"a longer source");assertEquals(15.0,cache.read("a.mp4"));assertEquals(2,calls.get());
        var racing=new CachingDurationReader(storage,p->{Files.writeString(path,"changed while probing");return 10.0;});
        assertNull(racing.read("a.mp4"),"A duration from a replaced source must not become a cache hit");
    }
    @Test void unavailableValuesExpireWithoutExtendingTheirDeadline()throws Exception {
        var clock=new AtomicLong();var calls=new AtomicInteger();
        var cache=new CachingDurationReader(storage,p->{calls.incrementAndGet();return null;},clock::get,2);
        assertNull(cache.read("a.mp4"));clock.set(TimeUnit.SECONDS.toNanos(14));assertNull(cache.read("a.mp4"));assertEquals(1,calls.get());
        clock.set(TimeUnit.SECONDS.toNanos(15));assertNull(cache.read("a.mp4"));assertEquals(2,calls.get());
    }
    @Test void positiveValuesUseBoundedLruEviction()throws Exception {
        var calls=new AtomicInteger();var cache=new CachingDurationReader(storage,p->{calls.incrementAndGet();return 5.0;},System::nanoTime,2);
        cache.read("a.mp4");cache.read("b.mp4");cache.read("a.mp4");cache.read("c.mp4");cache.read("b.mp4");assertEquals(4,calls.get());
    }
    @Test void overloadAndIoFailuresDoNotPoisonTheCache()throws Exception {
        var calls=new AtomicInteger();var cache=new CachingDurationReader(storage,p->{if(calls.getAndIncrement()==0)throw new RejectedExecutionException();return 7.0;});
        assertThrows(RejectedExecutionException.class,()->cache.read("a.mp4"));assertEquals(7.0,cache.read("a.mp4"));assertEquals(2,calls.get());
        var ioCalls=new AtomicInteger();var failing=new CachingDurationReader(storage,p->{ioCalls.incrementAndGet();throw new IOException("unavailable");});
        assertThrows(IOException.class,()->failing.read("b.mp4"));assertThrows(IOException.class,()->failing.read("b.mp4"));assertEquals(2,ioCalls.get());
    }
}
