package io.moviesites;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Fixed-expiry weighted LRU; duplicate loads share one bounded in-flight operation. */
final class ByteCache {
    @FunctionalInterface interface Loader { byte[] load() throws IOException; }
    record Value(byte[] bytes, String etag, long expires) {}
    record Result(Value value, long seconds) {}
    static class TooLarge extends IOException {}
    private final int maxEntries, maxLoads;
    private final long maxBytes;
    private final LongSupplier clock;
    private final Map<String,Value> values = new LinkedHashMap<>(16,.75f,true);
    private final Map<String,FutureTask<Value>> loads = new HashMap<>();
    private long bytes;
    ByteCache(int entries,long bytes,int loads) { this(entries,bytes,loads,System::nanoTime); }
    ByteCache(int entries,long bytes,int loads,LongSupplier clock) { maxEntries=entries;maxBytes=bytes;maxLoads=loads;this.clock=clock; }
    Result get(String key,long seconds,Loader loader) throws IOException {
        FutureTask<Value> pending; boolean leader=false;
        synchronized(this) {
            Value hit=values.get(key);
            if(hit!=null && hit.expires()>clock.getAsLong())return result(hit);
            if(hit!=null){values.remove(key);bytes-=hit.bytes().length;}
            pending=loads.get(key);
            if(pending==null) {
                if(loads.size()>=maxLoads)throw new RejectedExecutionException();
                pending=new FutureTask<>(()->{
                    byte[] body=loader.load();Value value=new Value(body,tag(body),clock.getAsLong()+TimeUnit.SECONDS.toNanos(seconds));
                    synchronized(this) {
                        if(body.length<=maxBytes) {
                            Value old=values.put(key,value);if(old!=null)bytes-=old.bytes().length;bytes+=body.length;
                            while(values.size()>maxEntries||bytes>maxBytes){var first=values.entrySet().iterator();var entry=first.next();bytes-=entry.getValue().bytes().length;first.remove();}
                        }
                    }
                    return value;
                });loads.put(key,pending);leader=true;
            }
        }
        try {
            if(leader)pending.run();
            return result(pending.get());
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}
        catch(ExecutionException e){if(e.getCause() instanceof IOException io)throw io;if(e.getCause() instanceof RuntimeException runtime)throw runtime;throw new IOException("Cache load failed",e.getCause());}
        finally{if(pending.isDone())synchronized(this){loads.remove(key,pending);}}
    }
    private Result result(Value value){return new Result(value,Math.max(0,TimeUnit.NANOSECONDS.toSeconds(value.expires()-clock.getAsLong())));}
    static String tag(byte[] body){try{return '"'+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body))+'"';}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static byte[] read(InputStream input,int max) throws IOException {try(input){byte[] bytes=input.readNBytes(max+1);if(bytes.length>max)throw new TooLarge();return bytes;}}
}
