package io.moviesites;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Short-lived, disk-backed query results for one directory; no recursive index or database. */
@Service
public class SortedDirectoryBrowser {
    public record Item(String name, String path, String kind, long size, String modified, Double duration, List<String> tags, String tagSource) {}
    public record Page(String path, String parent, List<Item> entries, String nextCursor, int warnings,
                       String state, String query, long examined, long analyzed, long unknown, String message) {}
    @FunctionalInterface interface DurationReader { Double read(String path) throws IOException; }
    private record CachedDuration(Double value, long time) {}
    private static final int FAN_IN = 16, MAX_RECORD = 32768, MAX_RUNS = 4096;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final Comparator<Item> ORDER = Comparator.comparingInt((Item i) -> i.kind().equals("folder") ? 0 : 1)
        .thenComparingDouble(i -> i.duration() == null ? Double.POSITIVE_INFINITY : i.duration()).thenComparing(Item::path);
    private final Settings settings;
    private final Storage storage;
    private final VideoTopics topics;
    private final DurationReader durations;
    private final MediaService media;
    private final int chunkSize;
    private final long maxQueryBytes;
    private final JsonMapper json = new JsonMapper();
    private final Map<String, Query> queries = new LinkedHashMap<>();
    private final Map<String, CachedDuration> durationCache = new LinkedHashMap<>(32, .75f, true);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), r -> daemon(r, "duration-sort"));
    private final ThreadPoolExecutor readers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4), r -> daemon(r, "sorted-page-reader"));
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "duration-sort-cleanup"));
    private static Thread daemon(Runnable r, String name) { Thread t = new Thread(r, name); t.setDaemon(true); return t; }
    private static final class Query {
        final String id = UUID.randomUUID().toString(), secret = UUID.randomUUID().toString(), path;
        final Set<Path> files = new HashSet<>();
        volatile long touched = System.nanoTime(), examined, analyzed, unknown;
        volatile String state = "preparing";
        volatile String message = "";
        volatile boolean cancelled, building = true;
        int warnings; long bytes; Path result;
        Query(String path) { this.path = path; }
    }
    @org.springframework.beans.factory.annotation.Autowired
    public SortedDirectoryBrowser(Settings settings, Storage storage, VideoTopics topics, MediaService media) throws IOException {
        this(settings, storage, topics, media::probeDuration, media, 128);
    }
    SortedDirectoryBrowser(Settings settings, Storage storage, VideoTopics topics, DurationReader durations, MediaService media, int chunkSize) throws IOException {
        this(settings, storage, topics, durations, media, chunkSize, MAX_BYTES);
    }
    SortedDirectoryBrowser(Settings settings, Storage storage, VideoTopics topics, DurationReader durations, MediaService media, int chunkSize, long maxQueryBytes) throws IOException {
        this.settings = settings; this.storage = storage; this.topics = topics; this.durations = durations; this.media = media; this.chunkSize = chunkSize;
        this.maxQueryBytes = maxQueryBytes;
        try (var files = Files.newDirectoryStream(settings.dataRoot().resolve("work"), "browse-*.bin")) {
            for (Path file : files) if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.getFileName().toString().matches("browse-[a-f0-9-]+\\.bin")) Files.delete(file);
        }
        cleanup.scheduleWithFixedDelay(this::retireIdle, 15, 15, TimeUnit.SECONDS);
    }
    public CompletableFuture<Page> browseAsync(String path, String cursor, String query) {
        try { return CompletableFuture.supplyAsync(() -> { try { return browse(path,cursor,query); } catch (IOException e) { throw new CompletionException(e); } },readers).orTimeout(8,TimeUnit.SECONDS); }
        catch (RejectedExecutionException e) { return CompletableFuture.failedFuture(e); }
    }
    public Page browse(String input, String cursor, String queryId) throws IOException {
        String path = storage.normalize(input);
        Query query; long offset = 0;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 3) throw new DirectoryBrowser.ExpiredCursor();
            query = find(parts[0], path);
            try { offset = Long.parseLong(parts[1]); } catch (NumberFormatException e) { throw new DirectoryBrowser.ExpiredCursor(); }
            if (offset < 0 || !MessageDigest.isEqual(signature(query, offset).getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) throw new DirectoryBrowser.ExpiredCursor();
        } else if (queryId != null && !queryId.isBlank()) query = find(queryId, path);
        else {
            retireIdle(); query = new Query(path);
            synchronized (queries) {
                if (queries.size() >= settings.maxBrowseSessions()) {
                    Query oldest = queries.values().stream().filter(q -> !q.building).min(Comparator.comparingLong(q -> q.touched)).orElse(null);
                    if (oldest != null) { queries.remove(oldest.id); discard(oldest); }
                }
                if (queries.size() >= settings.maxBrowseSessions()) throw new RejectedExecutionException();
                queries.put(query.id, query);
            }
            Query task = query;
            try { workers.execute(() -> prepare(task)); }
            catch (RejectedExecutionException e) { synchronized (queries) { queries.remove(task.id); } task.building = false; discard(task); throw e; }
        }
        synchronized (query) {
            if (query.cancelled) throw new DirectoryBrowser.ExpiredCursor();
            query.touched = System.nanoTime();
            if (!query.state.equals("ready")) return page(query, List.of(), null, "");
            List<Item> entries = new ArrayList<>(); long nextOffset = offset;
            try (var file = FileChannel.open(query.result, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                if (offset > file.size()) throw new DirectoryBrowser.ExpiredCursor();
                file.position(offset);
                for (int i = 0; i < settings.pageSize() && file.position() < file.size(); i++) entries.add(read(file));
                nextOffset = file.position();
                return page(query, entries, nextOffset < file.size() ? query.id + "." + nextOffset + "." + signature(query, nextOffset) : null, "");
            }
        }
    }
    private Query find(String id, String path) {
        synchronized (queries) {
            Query q = queries.get(id);
            if (q == null || !q.path.equals(path) || q.cancelled) throw new DirectoryBrowser.ExpiredCursor();
            return q;
        }
    }
    private String signature(Query q, long offset) { return Storage.digest(q.secret + "\0" + q.id + "\0" + offset); }
    private Page page(Query q, List<Item> entries, String next, String message) {
        String parent = q.path.isEmpty() ? null : q.path.contains("/") ? q.path.substring(0, q.path.lastIndexOf('/')) : "";
        if (q.state.equals("failed")) message = q.message;
        return new Page(q.path, parent, List.copyOf(entries), next, q.warnings, q.state, q.id, q.examined, q.analyzed, q.unknown, message);
    }
    private Double duration(String path) throws IOException {
        String key = storage.version(path); CachedDuration saved;
        synchronized (durationCache) { saved = durationCache.get(key); }
        if (saved != null && (saved.value() != null || System.nanoTime() - saved.time() < TimeUnit.SECONDS.toNanos(15))) return saved.value();
        Double value;
        try { value = durations.read(path); } catch (RejectedExecutionException e) { throw e; }
        catch (IOException | RuntimeException e) { value = null; }
        if (value != null && (!Double.isFinite(value) || value <= 0)) value = null;
        if (!key.equals(storage.version(path))) return null;
        synchronized (durationCache) {
            durationCache.put(key, new CachedDuration(value, System.nanoTime()));
            while (durationCache.size() > 2000) durationCache.remove(durationCache.keySet().iterator().next());
        }
        return value;
    }
    private void check(Query q) throws IOException {
        if (q.cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Query cancelled");
    }
    private void prepare(Query q) {
        try {
            List<Path> runs = new ArrayList<>(); List<Item> chunk = new ArrayList<>(chunkSize);
            try (var directory = storage.directory(q.path)) {
                for (Path file : directory) {
                    check(q); q.examined++; Path name = file.getFileName(); String filename = name.toString();
                    if (filename.equals(".git") || filename.equals(".gitkeep") || filename.equals(".DS_Store")) continue;
                    try {
                        var attributes = directory.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
                        if (attributes.isSymbolicLink()) { q.warnings++; continue; }
                        boolean folder = attributes.isDirectory();
                        if (!folder && !(attributes.isRegularFile() && DirectoryBrowser.isVideo(filename))) continue;
                        String path = q.path.isEmpty() ? filename : q.path + "/" + filename;
                        Double length = folder ? null : duration(path);
                        var labels = folder ? new VideoTopics.Topics(List.of(), "none", false) : topics.read(path);
                        if (!folder) { q.analyzed++; if (length == null) q.unknown++; }
                        if (labels.invalidMetadata()) q.warnings++;
                        chunk.add(new Item(filename, path, folder ? "folder" : "video", folder ? 0 : attributes.size(), attributes.lastModifiedTime().toInstant().toString(), length, labels.tags(), labels.source()));
                    } catch (IOException e) { q.warnings++; }
                    if (chunk.size() >= chunkSize) { if (runs.size() >= MAX_RUNS) throw new IOException("Query exceeds run limit"); runs.add(writeChunk(q, chunk)); chunk.clear(); }
                }
            }
            if (!chunk.isEmpty() || runs.isEmpty()) { if (runs.size() >= MAX_RUNS) throw new IOException("Query exceeds run limit"); runs.add(writeChunk(q, chunk)); }
            while (runs.size() > 1) {
                List<Path> merged = new ArrayList<>();
                for (int i = 0; i < runs.size(); i += FAN_IN) merged.add(merge(q, runs.subList(i, Math.min(runs.size(), i + FAN_IN))));
                runs = merged;
            }
            synchronized (q) { check(q); q.result = runs.getFirst(); q.state = "ready"; }
        } catch (IOException | RuntimeException e) {
            q.message = e instanceof RejectedExecutionException ? "처리 중인 영상이 많습니다. 잠시 후 새로고침해 주세요." : "영상 목록을 정리하지 못했습니다. 저장소 연결·여유 공간을 확인한 뒤 새로고침해 주세요.";
            q.state = "failed";
        }
        finally { synchronized (q) { q.building = false; if (q.cancelled || !q.state.equals("ready")) deleteFiles(q); } }
    }
    private Path create(Query q) throws IOException {
        check(q);
        if (media != null) media.enforceCacheBudget();
        Path file = settings.dataRoot().resolve("work").resolve("browse-" + q.id + "-" + UUID.randomUUID() + ".bin");
        Files.createFile(file); synchronized (q) { q.files.add(file); } return file;
    }
    private Path writeChunk(Query q, List<Item> entries) throws IOException {
        entries.sort(ORDER); Path file = create(q);
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)))) {
            for (Item entry : entries) write(q, output, entry);
        }
        return file;
    }
    private void write(Query q, DataOutputStream output, Item item) throws IOException {
        check(q); byte[] bytes = json.writeValueAsBytes(item);
        if (bytes.length > MAX_RECORD || q.bytes + 4 + bytes.length > maxQueryBytes) throw new IOException("Query exceeds temporary-storage budget");
        if (Files.getFileStore(settings.dataRoot()).getUsableSpace() - bytes.length - 4 < settings.minFreeBytes()) throw new IOException("Insufficient free space");
        q.bytes += 4 + bytes.length; output.writeInt(bytes.length); output.write(bytes);
    }
    private Item read(FileChannel channel) throws IOException {
        ByteBuffer size = ByteBuffer.allocate(4); readFully(channel, size); size.flip(); int length = size.getInt();
        if (length < 1 || length > MAX_RECORD) throw new IOException("Invalid query record");
        ByteBuffer bytes = ByteBuffer.allocate(length); readFully(channel, bytes); return json.readValue(bytes.array(), Item.class);
    }
    private static void readFully(FileChannel channel, ByteBuffer bytes) throws IOException { while (bytes.hasRemaining()) if (channel.read(bytes) < 0) throw new EOFException(); }
    private record Head(Item item, FileChannel channel) {}
    private Path merge(Query q, List<Path> sources) throws IOException {
        if (sources.size() == 1) return sources.getFirst();
        Path target = create(q); List<FileChannel> inputs = new ArrayList<>();
        PriorityQueue<Head> heads = new PriorityQueue<>((a, b) -> ORDER.compare(a.item(), b.item()));
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)))) {
            for (Path source : sources) { var input = FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); inputs.add(input); if (input.size() > 0) heads.add(new Head(read(input), input)); }
            while (!heads.isEmpty()) { Head head = heads.remove(); write(q, output, head.item()); if (head.channel().position() < head.channel().size()) heads.add(new Head(read(head.channel()), head.channel())); }
        } finally { for (var input : inputs) input.close(); }
        for (Path source : sources) { long size = Files.size(source); Files.delete(source); synchronized (q) { q.files.remove(source); } q.bytes -= size; }
        return target;
    }
    private void deleteFiles(Query q) { for (Path file : q.files) try { Files.deleteIfExists(file); } catch (IOException ignored) {} q.files.clear(); }
    private void discard(Query q) { synchronized (q) { q.cancelled = true; if (!q.building) deleteFiles(q); } }
    private void retireIdle() {
        synchronized (queries) {
            var iterator = queries.values().iterator();
            while (iterator.hasNext()) { Query q = iterator.next(); long seconds = q.building ? 15 : settings.browseIdleSeconds(); if (System.nanoTime() - q.touched > TimeUnit.SECONDS.toNanos(seconds)) { iterator.remove(); discard(q); } }
        }
    }
    @PreDestroy public void close() { cleanup.shutdownNow(); readers.shutdownNow(); synchronized (queries) { for (Query q : queries.values()) discard(q); queries.clear(); } workers.shutdownNow(); }
}
