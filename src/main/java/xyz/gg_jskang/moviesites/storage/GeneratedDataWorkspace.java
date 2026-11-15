package xyz.gg_jskang.moviesites.storage;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.internal.CacheBudget;
/** Owns the exclusive writable generated-data root, never the original media. */
@Component
public final class GeneratedDataWorkspace implements AutoCloseable {
    private final Settings settings;
    private final CacheBudget budget;
    private final Path dataRootReal;
    private final FileChannel lockChannel;
    private final FileLock dataLock;
    private final Path directoryLock, ownerMarker;
    private boolean directoryOwned;
    public GeneratedDataWorkspace(Settings settings) throws IOException {
        this.settings = settings;
        Files.createDirectories(settings.dataRoot());
        dataRootReal = settings.dataRoot().toRealPath();
        directoryLock=dataRootReal.resolve(".moviesites.owner");
        ownerMarker=directoryLock.resolve(java.util.UUID.randomUUID().toString());
        lockChannel=FileChannel.open(dataRootReal.resolve(".moviesites.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try{FileLock lock=lockChannel.tryLock();if(lock==null)throw new IOException("Data root is already in use");dataLock=lock;}
        catch(IOException|RuntimeException e){lockChannel.close();throw new IOException("Data root is already in use or cannot be locked",e);}
        try {
            // Some Docker Desktop bind mounts do not share advisory locks across containers.
            // Atomic directory creation adds a fail-closed cross-mount ownership guard.
            try { Files.createDirectory(directoryLock); }
            catch(FileAlreadyExistsException e){throw new IOException("Data root is already in use or needs ownership-lock recovery",e);}
            directoryOwned=true;
            Files.writeString(ownerMarker,"pid="+ProcessHandle.current().pid()+"\n",StandardOpenOption.CREATE_NEW);
            for (String name : new String[]{"thumbnails", "playback", "work"}) {
            Path p = settings.dataRoot().resolve(name);
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("Data subdirectories must not be symbolic links");
            Files.createDirectories(p);
        }
            budget=new CacheBudget(settings);
        } catch(IOException|RuntimeException e){close();throw e;}
    }
    @PreDestroy public synchronized void close()throws IOException{
        try {
            if(directoryOwned){Files.deleteIfExists(ownerMarker);Files.deleteIfExists(directoryLock);directoryOwned=false;}
        } finally {try{if(dataLock.isValid())dataLock.release();}finally{lockChannel.close();}}
    }
    public Path root() { return dataRootReal; }
    public void enforceBudget() throws IOException { budget.enforce(); }
    public void pin(Path path) { budget.pin(path); }
    public void unpin(Path path) { budget.unpin(path); }
}
