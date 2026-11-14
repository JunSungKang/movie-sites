package io.moviesites;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

/** On-demand subtree sampling, never a persistent index or an unbounded browser catalog. */
@Service
public class RandomPlayback implements AutoCloseable {
    public record Item(String name, String path, long size, double duration, String version) {}
    public record Page(String state, String query, String path, int minutes, List<Item> entries,
                       double totalSeconds, long examined, long videos, int checked, int skipped,
                       boolean sampled, boolean limited, String message) {}
    record Limits(int candidates, int catalogs, long entries, int depth, int checks, long timeoutMillis) {}
    @FunctionalInterface interface DurationReader { Double read(String path) throws IOException; }
    private record Candidate(String name, String path, long size, String version) {}
    private record Catalog(List<Candidate> items, long examined, long videos, int warnings, long created) {}
    private record Frame(String path, SecureDirectoryStream<Path> directory, Iterator<Path> iterator, int depth) implements AutoCloseable {
        public void close() throws IOException { directory.close(); }
    }
    private static final Set<Integer> MINUTES = Set.of(10, 20, 30, 40, 50);
    private final Settings settings;
    private final Storage storage;
    private final DurationReader durations;
    private final Limits limits;
    private final Map<String, Query> queries = new LinkedHashMap<>();
    private final Map<String, Catalog> catalogs = new LinkedHashMap<>(8, .75f, true);
    private final Map<String, Double> durationCache = new LinkedHashMap<>(32, .75f, true);
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), r -> daemon(r, "random-playback"));
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "random-playback-cleanup"));
    private static Thread daemon(Runnable r, String name) { Thread t = new Thread(r, name); t.setDaemon(true); return t; }
    private static final class Query {
        final String id = UUID.randomUUID().toString(), path;
        final int minutes;
        final long deadline;
        volatile long touched = System.nanoTime(), examined, videos;
        volatile int checked, skipped;
        volatile boolean cancelled, sampled, limited;
        volatile String state = "preparing", message = "";
        volatile List<Item> items = List.of();
        volatile long totalMillis;
        FutureTask<Void> future;
        Query(String path, int minutes, long timeoutMillis) {
            this.path = path; this.minutes = minutes;
            deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }
    }
    @org.springframework.beans.factory.annotation.Autowired
    public RandomPlayback(Settings settings, Storage storage, MediaService media) {
        this(settings, storage, media::probeDuration, new Limits(512, 4, 250_000, 64, 128, 120_000));
    }
    RandomPlayback(Settings settings, Storage storage, DurationReader durations, Limits limits) {
        this.settings = settings; this.storage = storage; this.durations = durations; this.limits = limits;
        cleanup.scheduleWithFixedDelay(this::retire, 5, 5, TimeUnit.SECONDS);
    }
    public Page request(String input, int minutes, String id, boolean refresh) {
        String path = storage.normalize(input);
        if (!MINUTES.contains(minutes)) throw new IllegalArgumentException("Unsupported duration");
        Query q;
        synchronized (queries) {
            if (id != null && !id.isBlank()) q = find(path, minutes, id);
            else {
                retire();
                if (queries.size() >= settings.maxBrowseSessions()) {
                    Query oldest = queries.values().stream().filter(candidate -> !candidate.state.equals("preparing"))
                            .min(Comparator.comparingLong(candidate -> candidate.touched)).orElse(null);
                    if (oldest != null) queries.remove(oldest.id);
                }
                if (queries.size() >= settings.maxBrowseSessions()) throw new RejectedExecutionException();
                q = new Query(path, minutes, limits.timeoutMillis());
                Query task = q;
                q.future = new FutureTask<>(() -> { prepare(task, refresh); return null; });
                queries.put(q.id, q);
                try { worker.execute(q.future); }
                catch (RejectedExecutionException e) { queries.remove(q.id); throw e; }
            }
            q.touched = System.nanoTime();
        }
        synchronized (q) {
            return new Page(q.state, q.id, q.path, q.minutes, q.items, q.totalMillis / 1000.0,
                    q.examined, q.videos, q.checked, q.skipped, q.sampled, q.limited, q.message);
        }
    }
    private Query find(String path, int minutes, String id) {
        Query q = queries.get(id);
        if (q == null || q.cancelled || !q.path.equals(path) || q.minutes != minutes) throw new DirectoryBrowser.ExpiredCursor();
        return q;
    }
    public void cancel(String input, int minutes, String id) {
        String path = storage.normalize(input);
        synchronized (queries) {
            if (!queries.containsKey(id)) return;
            Query q = find(path, minutes, id);
            queries.remove(id); q.cancelled = true; q.future.cancel(true); worker.purge();
        }
    }
    private void check(Query q) throws IOException {
        if (q.cancelled || !q.state.equals("preparing") || Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        if (System.nanoTime() > q.deadline) throw new IOException("Preparation deadline reached");
    }
    private Catalog catalog(Query q, boolean refresh) throws IOException {
        synchronized (catalogs) {
            Catalog saved = catalogs.get(q.path);
            if (!refresh && saved != null && System.nanoTime() - saved.created() < TimeUnit.SECONDS.toNanos(settings.browserCacheTtlSeconds())) return saved;
        }
        var sample = new ArrayList<Candidate>(limits.candidates());
        Deque<Frame> stack = new ArrayDeque<>(); int warnings = 0;
        var random = ThreadLocalRandom.current();
        try {
            var root = storage.directory(q.path);
            stack.push(new Frame(q.path, root, root.iterator(), 0));
            while (!stack.isEmpty()) {
                check(q); Frame frame = stack.peek();
                if (!frame.iterator().hasNext()) { stack.pop().close(); continue; }
                Path name = frame.iterator().next().getFileName(); String filename = name.toString();
                if (++q.examined > limits.entries()) throw new IOException("Entry limit reached");
                if (Set.of(".git", ".gitkeep", ".DS_Store").contains(filename)) continue;
                String path = frame.path().isEmpty() ? filename : frame.path() + "/" + filename;
                if (path.length() > 4096) { warnings++; continue; }
                try {
                    var a = frame.directory().getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
                    if (a.isSymbolicLink()) { warnings++; continue; }
                    if (a.isDirectory()) {
                        if (frame.depth() >= limits.depth()) throw new DepthLimit();
                        var child = frame.directory().newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
                        stack.push(new Frame(path, child, child.iterator(), frame.depth() + 1));
                    } else if (a.isRegularFile() && DirectoryBrowser.isVideo(filename)) {
                        q.videos++;
                        String version = Storage.digest(settings.mediaRoot() + "\0" + path + "\0" + a.size() + "\0" + a.lastModifiedTime().toMillis() + "\0" + a.fileKey());
                        var item = new Candidate(filename, path, a.size(), version);
                        // Reservoir sampling gives every discovered video a chance without retaining the tree.
                        if (sample.size() < limits.candidates()) sample.add(item);
                        else { long index = random.nextLong(q.videos); if (index < sample.size()) sample.set((int) index, item); }
                    }
                } catch (DepthLimit e) { throw new IOException("Depth limit reached"); }
                catch (IOException e) { warnings++; }
            }
        } finally {
            while (!stack.isEmpty()) try { stack.pop().close(); } catch (IOException ignored) {}
        }
        check(q);
        Catalog result = new Catalog(List.copyOf(sample), q.examined, q.videos, warnings, System.nanoTime());
        synchronized (catalogs) {
            catalogs.put(q.path, result);
            while (catalogs.size() > limits.catalogs()) catalogs.remove(catalogs.keySet().iterator().next());
        }
        return result;
    }
    private static class DepthLimit extends IOException {}
    private void prepare(Query q, boolean refresh) {
        try {
            Catalog catalog = catalog(q, refresh); check(q);
            q.examined = catalog.examined(); q.videos = catalog.videos(); q.skipped = catalog.warnings();
            q.sampled = catalog.videos() > catalog.items().size();
            var candidates = new ArrayList<>(catalog.items()); Collections.shuffle(candidates);
            var selected = new ArrayList<Item>(); long remaining = q.minutes * 60_000L;
            int visited = 0;
            for (Candidate candidate : candidates) {
                check(q);
                if (selected.size() >= 200 || q.checked >= limits.checks() || remaining == 0) break;
                visited++; q.checked++;
                try {
                    if (!candidate.version().equals(storage.version(candidate.path()))) { q.skipped++; continue; }
                    Double length = durationCache.get(candidate.version());
                    if (length == null) {
                        length = durations.read(candidate.path());
                        if (length != null && Double.isFinite(length) && length > 0) {
                            durationCache.put(candidate.version(), length);
                            while (durationCache.size() > 2000) durationCache.remove(durationCache.keySet().iterator().next());
                        }
                    }
                    check(q);
                    if (length == null || !Double.isFinite(length) || length <= 0 || !candidate.version().equals(storage.version(candidate.path()))) { q.skipped++; continue; }
                    double millis = Math.ceil(length * 1000);
                    if (millis > remaining) continue;
                    selected.add(new Item(candidate.name(), candidate.path(), candidate.size(), length, candidate.version()));
                    remaining -= (long) millis;
                } catch (IOException e) { check(q); q.skipped++; }
            }
            synchronized (q) {
                check(q); q.limited = visited < candidates.size() && remaining > 0;
                q.items = List.copyOf(selected); q.totalMillis = q.minutes * 60_000L - remaining;
                q.message = selected.isEmpty() ? "이번에는 이 시간 안에 끝까지 볼 수 있는 영상을 찾지 못했어요. 다른 시간으로 다시 골라 주세요." : "";
                q.state = "ready";
            }
        } catch (IOException | RuntimeException e) {
            synchronized (q) {
                if (!q.cancelled && q.state.equals("preparing")) {
                    q.message = e instanceof RejectedExecutionException ? "영상 처리가 몰려 있어요. 잠시 후 다시 골라 주세요." : "영상을 모두 확인하지 못했어요. 더 작은 카테고리에서 다시 시도하거나 저장소 연결을 확인해 주세요.";
                    q.state = "failed";
                }
            }
        }
    }
    private void retire() {
        synchronized (queries) {
            var it = queries.values().iterator(); long now = System.nanoTime();
            while (it.hasNext()) {
                Query q = it.next();
                if (q.state.equals("preparing") && now > q.deadline) {
                    synchronized (q) { q.state = "failed"; q.message = "목록을 준비하는 데 시간이 오래 걸려요. 더 작은 카테고리에서 다시 골라 주세요."; }
                    q.future.cancel(true);
                }
                long idle = q.state.equals("preparing") ? 15 : settings.browserCacheTtlSeconds();
                if (now - q.touched > TimeUnit.SECONDS.toNanos(idle)) { it.remove(); q.cancelled = true; q.future.cancel(true); }
            }
            worker.purge();
        }
    }
    @Override @PreDestroy public void close() {
        cleanup.shutdownNow();
        synchronized (queries) { for (Query q : queries.values()) { q.cancelled = true; q.future.cancel(true); } queries.clear(); }
        worker.shutdownNow();
        synchronized (catalogs) { catalogs.clear(); }
    }
}
