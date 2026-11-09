package io.moviesites;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class Storage implements AutoCloseable {
    private final Settings settings;
    private final Path dataRootReal;
    private final FileChannel lockChannel;
    private final FileLock dataLock;
    private final Path directoryLock, ownerMarker;
    private boolean directoryOwned;
    public Storage(Settings settings) throws IOException {
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
        }} catch(IOException|RuntimeException e){close();throw e;}
    }
    @PreDestroy public synchronized void close()throws IOException{
        try {
            if(directoryOwned){Files.deleteIfExists(ownerMarker);Files.deleteIfExists(directoryLock);directoryOwned=false;}
        } finally {try{if(dataLock.isValid())dataLock.release();}finally{lockChannel.close();}}
    }
    public String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        if (input.length() > 4096 || input.indexOf('\0') >= 0 || input.contains("\\")) throw new IllegalArgumentException("Invalid path");
        Path p = Path.of(input);
        if (p.isAbsolute()) throw new IllegalArgumentException("Only relative media paths are accepted");
        for (Path part : p) if (part.toString().equals("..") || part.toString().equals(".")) throw new IllegalArgumentException("Invalid path component");
        return p.normalize().toString().replace('\\', '/');
    }
    @SuppressWarnings("unchecked")
    public SecureDirectoryStream<Path> directory(String input) throws IOException {
        String rel = normalize(input);
        Path root;
        try { root = settings.mediaRoot().toRealPath(); } catch (IOException e) { throw new IOException("Media storage unavailable", e); }
        if(root.startsWith(dataRootReal)||dataRootReal.startsWith(root))throw new IllegalArgumentException("Media and data roots overlap");
        DirectoryStream<Path> first = Files.newDirectoryStream(root);
        if (!(first instanceof SecureDirectoryStream<?>)) { first.close(); throw new IOException("Secure directory access is unavailable on this filesystem"); }
        SecureDirectoryStream<Path> current = (SecureDirectoryStream<Path>)first;
        try {
            if (!rel.isEmpty()) for (Path part : Path.of(rel)) {
                SecureDirectoryStream<Path> next = current.newDirectoryStream(part, LinkOption.NOFOLLOW_LINKS);
                current.close(); current = next;
            }
            return current;
        } catch (IOException | RuntimeException e) { current.close(); throw e; }
    }
    public BasicFileAttributes attributes(String input) throws IOException {
        String rel = normalize(input);
        if (rel.isEmpty()) throw new IllegalArgumentException("A file path is required");
        Path p = Path.of(rel);
        try (var parent = directory(p.getParent() == null ? "" : p.getParent().toString())) {
            BasicFileAttributes a = parent.getFileAttributeView(p.getFileName(), BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            if (!a.isRegularFile()) throw new NoSuchFileException("Media is not a regular file");
            return a;
        }
    }
    public SeekableByteChannel open(String input) throws IOException {
        String rel = normalize(input); Path p = Path.of(rel);
        try (var parent = directory(p.getParent() == null ? "" : p.getParent().toString())) {
            BasicFileAttributes a = parent.getFileAttributeView(p.getFileName(), BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            if (!a.isRegularFile()) throw new NoSuchFileException("Media is not a regular file");
            return parent.newByteChannel(p.getFileName(), Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        }
    }
    public Path processInput(String input) throws IOException {
        String rel = normalize(input);
        attributes(rel);
        Path root = settings.mediaRoot().toRealPath();
        Path target = root.resolve(rel).toRealPath();
        if (!target.startsWith(root) || Files.isSymbolicLink(target)) throw new AccessDeniedException("Outside media root");
        return target;
    }
    public String version(String path) throws IOException {
        var a = attributes(path);
        return digest(settings.mediaRoot() + "\0" + normalize(path) + "\0" + a.size() + "\0" + a.lastModifiedTime().toMillis() + "\0" + a.fileKey());
    }
    public String rootId() { return digest(settings.mediaRoot().toString()).substring(0, 16); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
