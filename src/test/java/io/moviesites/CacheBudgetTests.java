package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheBudgetTests {
    @TempDir Path temp;
    Settings settings(){return new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,4,1_048_576,0,"ffmpeg","ffprobe");}
    Path file(Settings s,String path,int size)throws Exception{Path p=s.dataRoot().resolve(path);Files.write(p,new byte[size]);return p;}
    @Test void growingOutputEvictsCompletedCacheAndLaterRequestsCanContinue()throws Exception{
        var s=settings();Files.createDirectories(s.mediaRoot());Path original=s.mediaRoot().resolve("original.mp4");Files.writeString(original,"original");
        try(var storage=new Storage(s)){
            var budget=new CacheBudget(s);
            Path old=file(s,"playback/old.mp4",800_000);Files.setLastModifiedTime(old,FileTime.fromMillis(1000));
            Path partial=file(s,"work/"+"a".repeat(64)+".part.mp4",500_000);
            budget.enforce();assertFalse(Files.exists(old));assertTrue(Files.exists(partial));
            Path completed=s.dataRoot().resolve("playback/first.mp4");Files.move(partial,completed);
            Files.setLastModifiedTime(completed,FileTime.fromMillis(2000));
            Path next=file(s,"work/"+"b".repeat(64)+".part.mp4",700_000);
            budget.enforce();assertFalse(Files.exists(completed));assertEquals(700_000,Files.size(next));assertEquals("original",Files.readString(original));
        }
    }
    @Test void activePlaybackIsPinnedUntilAllStreamsReleaseIt()throws Exception{
        var s=settings();try(var storage=new Storage(s)){
            var budget=new CacheBudget(s);Path active=file(s,"playback/active.mp4",800_000);Path partial=file(s,"work/"+"c".repeat(64)+".part.mp4",500_000);
            budget.pin(active);budget.pin(active);assertThrows(IOException.class,budget::enforce);assertTrue(Files.exists(active));
            budget.unpin(active);assertThrows(IOException.class,budget::enforce);
            budget.unpin(active);budget.enforce();assertFalse(Files.exists(active));assertTrue(Files.exists(partial));
        }
    }
    @Test void restartReclaimsOnlyRecognizedInterruptedWorkAndAllowsRetry()throws Exception{
        var s=settings();try(var storage=new Storage(s)){
            Path partial=file(s,"work/"+"d".repeat(64)+".part.mp4",900_000);
            Path thumb=file(s,"work/"+"e".repeat(64)+".part.jpg",100_000);
            Path log=file(s,"work/process-12345.log",200_000);Path unrelated=file(s,"work/keep.txt",20);
            var budget=new CacheBudget(s);assertFalse(Files.exists(partial));assertFalse(Files.exists(thumb));assertFalse(Files.exists(log));assertTrue(Files.exists(unrelated));
            Path retry=file(s,"work/"+"d".repeat(64)+".part.mp4",700_000);budget.enforce();assertTrue(Files.exists(retry));
        }
    }
    @Test void oversizedWorkFailsWithoutDeletingActiveOutput()throws Exception{
        var s=settings();try(var storage=new Storage(s)){
            var budget=new CacheBudget(s);Path partial=file(s,"work/"+"f".repeat(64)+".part.mp4",1_100_000);
            assertThrows(IOException.class,budget::enforce);assertTrue(Files.exists(partial));
            Files.delete(partial);budget.enforce();
        }
    }
    @Test void evictionContinuesBeyondTheBoundedCandidateWindow()throws Exception{
        var s=settings();try(var storage=new Storage(s)){
            var budget=new CacheBudget(s);
            for(int i=0;i<1100;i++)file(s,"thumbnails/"+i+".jpg",1000);
            Path partial=file(s,"work/"+"a".repeat(64)+".part.mp4",1_048_000);budget.enforce();
            try(var files=Files.list(s.dataRoot().resolve("thumbnails"))){assertEquals(0,files.count());}
            assertEquals(1_048_000,Files.size(partial));
        }
    }
    @Test void dataRootHasOneOwnerAndIsReleasedOnClose()throws Exception{
        var s=settings();try(var first=new Storage(s)){assertThrows(IOException.class,()->new Storage(s));}
        try(var reopened=new Storage(s)){assertNotNull(reopened);}
    }
}
