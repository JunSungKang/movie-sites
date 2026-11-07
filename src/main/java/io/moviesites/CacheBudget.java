package io.moviesites;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded-memory eviction of generated files; never enumerates the media root. */
final class CacheBudget {
    private final Settings settings;
    private final Map<Path,AtomicInteger> pinned=new HashMap<>();
    private record Candidate(Path path,long modified,long size){}
    CacheBudget(Settings settings)throws IOException{this.settings=settings;recoverInterruptedWork();}
    private void recoverInterruptedWork()throws IOException{
        try(var files=Files.newDirectoryStream(settings.dataRoot().resolve("work"))){
            for(Path file:files){String name=file.getFileName().toString();if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&(name.matches("[a-f0-9]{64}\\.part\\.(mp4|jpg)")||name.matches("process-[A-Za-z0-9_-]+\\.log")))Files.deleteIfExists(file);}
        }
    }
    synchronized void pin(Path path){if(path!=null)pinned.computeIfAbsent(path,p->new AtomicInteger()).incrementAndGet();}
    synchronized void unpin(Path path){if(path!=null)pinned.computeIfPresent(path,(p,n)->n.decrementAndGet()<=0?null:n);}
    synchronized void enforce()throws IOException{
        while(true){
            long total=0;
            PriorityQueue<Candidate> oldest=new PriorityQueue<>(Comparator.comparingLong(Candidate::modified).reversed());
            for(String name:List.of("thumbnails","playback","work"))try(var files=Files.newDirectoryStream(settings.dataRoot().resolve(name))){
                for(Path path:files){
                    BasicFileAttributes attributes;
                    try{attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}catch(NoSuchFileException e){continue;}
                    if(!attributes.isRegularFile())continue;
                    long size=attributes.size();total+=size;
                    if(name.equals("work")||pinned.containsKey(path))continue;
                    Candidate candidate=new Candidate(path,attributes.lastModifiedTime().toMillis(),size);
                    if(oldest.size()<1024)oldest.add(candidate);else if(candidate.modified()<oldest.peek().modified()){oldest.poll();oldest.add(candidate);}
                }
            }
            long free=Files.getFileStore(settings.dataRoot()).getUsableSpace();
            if(total<=settings.maxCacheBytes()&&free>=settings.minFreeBytes())return;
            if(oldest.isEmpty())throw new IOException("No reclaimable cache space is available");
            List<Candidate> candidates=new ArrayList<>(oldest);candidates.sort(Comparator.comparingLong(Candidate::modified));boolean removed=false;
            for(var candidate:candidates){
                if(total<=settings.maxCacheBytes()&&free>=settings.minFreeBytes())return;
                if(Files.deleteIfExists(candidate.path())){total-=candidate.size();free+=candidate.size();removed=true;}
            }
            if(!removed)throw new IOException("Could not reclaim cache space");
        }
    }
}
