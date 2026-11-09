package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageLockTests {
    @TempDir Path temp;
    Settings settings(){return new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");}
    @Test void normalCloseReleasesBothGuardsAndAllowsRestart()throws Exception {
        var settings=settings();Path guard=settings.dataRoot().resolve(".moviesites.owner");
        try(var first=new Storage(settings)){
            assertTrue(Files.isDirectory(guard));
            assertThrows(IOException.class,()->new Storage(settings));
            assertTrue(Files.isDirectory(guard));
        }
        assertFalse(Files.exists(guard));
        try(var restarted=new Storage(settings)){assertTrue(Files.isDirectory(guard));}
        assertFalse(Files.exists(guard));
    }
    @Test void uncleanOwnershipFailsClosedWithoutDeletingTheExistingOwner()throws Exception {
        var settings=settings();Path guard=Files.createDirectories(settings.dataRoot().resolve(".moviesites.owner"));
        Path previous=Files.writeString(guard.resolve("previous-owner"),"preserve this ownership evidence");
        IOException error=assertThrows(IOException.class,()->new Storage(settings));
        assertTrue(error.getMessage().contains("already in use"));
        assertEquals("preserve this ownership evidence",Files.readString(previous));
        Files.delete(previous);Files.delete(guard);
        try(var recovered=new Storage(settings)){assertTrue(Files.isDirectory(guard));}
    }
}
