package xyz.gg_jskang.moviesites.media.internal;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import xyz.gg_jskang.moviesites.media.DurationReader;
import xyz.gg_jskang.moviesites.storage.MediaStore;
/** A version-aware bounded decorator; unavailable durations expire after 15 seconds. */
public final class CachingDurationReader implements DurationReader {
    private record Value(Double seconds,long created) {}
    private final MediaStore storage;
    private final DurationReader source;
    private final LongSupplier clock;
    private final int maxEntries;
    private final Map<String,Value> values=new LinkedHashMap<>(32,.75f,true);
    public CachingDurationReader(MediaStore storage,DurationReader source){this(storage,source,System::nanoTime,2000);}
    CachingDurationReader(MediaStore storage,DurationReader source,LongSupplier clock,int maxEntries){this.storage=storage;this.source=source;this.clock=clock;this.maxEntries=maxEntries;}
    @Override public Double read(String path)throws IOException {
        String version=storage.version(path);Value saved;
        synchronized(values){saved=values.get(version);}
        if(saved!=null&&(saved.seconds()!=null||clock.getAsLong()-saved.created()<TimeUnit.SECONDS.toNanos(15)))return saved.seconds();
        Double seconds=source.read(path);
        if(seconds!=null&&(!Double.isFinite(seconds)||seconds<=0))seconds=null;
        if(!version.equals(storage.version(path)))return null;
        synchronized(values){values.put(version,new Value(seconds,clock.getAsLong()));while(values.size()>maxEntries)values.remove(values.keySet().iterator().next());}
        return seconds;
    }
}
