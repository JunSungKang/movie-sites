package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RandomPlaybackTests {
    @TempDir Path temp;
    Settings settings; Storage storage;
    final RandomPlayback.Limits defaults = new RandomPlayback.Limits(512, 4, 250_000, 64, 128, 120_000);
    @BeforeEach void setup() throws Exception {
        settings = new Settings(temp.resolve("media"), temp.resolve("data"), 120, 48, 8, 120, 4, 10_737_418_240L, 0, "ffmpeg", "ffprobe");
        Files.createDirectories(settings.mediaRoot()); storage = new Storage(settings);
    }
    @AfterEach void close() throws Exception { storage.close(); }
    void video(String path) throws Exception { Path p = settings.mediaRoot().resolve(path); Files.createDirectories(p.getParent()); Files.writeString(p, "fixture"); }
    RandomPlayback.Page ready(RandomPlayback random, String path, int minutes) throws Exception {
        var page = random.request(path, minutes, null, false);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (page.state().equals("preparing") && System.nanoTime() < deadline) {
            Thread.sleep(5); page = random.request(path, minutes, page.query(), false);
        }
        return page;
    }
    @Test void rootIncludesEveryCategoryAndCategoryIsComponentBoundedIncludingDescendants() throws Exception {
        for (String p : List.of("root.mp4", "A/one.mp4", "A/deep/two.mp4", "A-other/three.mp4", "B/four.mp4")) video(p);
        try (var random = new RandomPlayback(settings, storage, p -> 30.0, defaults)) {
            var all = ready(random, "", 10); assertEquals("ready", all.state()); assertEquals(5, all.entries().size());
            var category = ready(random, "A", 10);
            assertEquals(Set.of("A/one.mp4", "A/deep/two.mp4"), new HashSet<>(category.entries().stream().map(RandomPlayback.Item::path).toList()));
            assertEquals(60.0, category.totalSeconds());
        }
    }
    @Test void everyBudgetUsesWholeUniqueFiniteVideosAndNeverRoundsOverLimit() throws Exception {
        for (int i = 0; i < 80; i++) video("clips/" + i + ".mp4");
        video("clips/invalid.mp4"); video("clips/long.mp4");
        try (var random = new RandomPlayback(settings, storage, p -> p.contains("invalid") ? Double.NaN : p.contains("long") ? 3600.0 : 59.9994, defaults)) {
            for (int minutes : List.of(10, 20, 30, 40, 50)) {
                var page = ready(random, "clips", minutes); assertEquals("ready", page.state());
                assertFalse(page.entries().isEmpty()); assertTrue(page.totalSeconds() <= minutes * 60);
                assertTrue(page.entries().stream().mapToDouble(RandomPlayback.Item::duration).sum() <= minutes * 60);
                assertEquals(page.entries().size(), new HashSet<>(page.entries().stream().map(RandomPlayback.Item::path).toList()).size());
                assertTrue(page.entries().stream().allMatch(e -> e.duration() == 59.9994));
            }
        }
    }
    @Test void queryAndCancellationBindToExactScopeAndBudgetAndRejectTraversalAndUnsupportedTimes() throws Exception {
        video("A/clip.mp4");
        try (var random = new RandomPlayback(settings, storage, p -> 60.0, defaults)) {
            var page = ready(random, "A", 10);
            assertThrows(DirectoryBrowser.ExpiredCursor.class, () -> random.request("", 10, page.query(), false));
            assertThrows(DirectoryBrowser.ExpiredCursor.class, () -> random.request("A", 20, page.query(), false));
            assertThrows(DirectoryBrowser.ExpiredCursor.class, () -> random.cancel("", 10, page.query()));
            assertThrows(IllegalArgumentException.class, () -> random.request("../outside", 10, null, false));
            assertThrows(IllegalArgumentException.class, () -> random.request("A", 15, null, false));
            random.cancel("A", 10, page.query());
            assertThrows(DirectoryBrowser.ExpiredCursor.class, () -> random.request("A", 10, page.query(), false));
        }
    }
    @Test void symlinksAreNotFollowedAndSampleAndProbeLimitsAreExplicit() throws Exception {
        for (int i = 0; i < 100; i++) video("A/" + i + ".mp4");
        Path outside = temp.resolve("outside"); Files.createDirectory(outside); Files.writeString(outside.resolve("escape.mp4"), "outside");
        Files.createSymbolicLink(settings.mediaRoot().resolve("linked"), outside);
        Files.createSymbolicLink(settings.mediaRoot().resolve("A/linked.mp4"), outside.resolve("escape.mp4"));
        var limits = new RandomPlayback.Limits(10, 2, 1000, 5, 3, 120_000);
        try (var random = new RandomPlayback(settings, storage, p -> { assertTrue(p.startsWith("A/")); assertFalse(p.contains("linked")); return 60.0; }, limits)) {
            var page = ready(random, "", 50);
            assertEquals(100, page.videos()); assertTrue(page.sampled()); assertTrue(page.limited());
            assertEquals(3, page.checked()); assertEquals(3, page.entries().size()); assertTrue(page.skipped() >= 2);
        }
    }
    @Test void entryAndDepthLimitsFailInsteadOfReturningAPartialRootPlaylist() throws Exception {
        for (int i = 0; i < 10; i++) video("A/" + i + ".mp4");
        for (var limits : List.of(new RandomPlayback.Limits(10, 2, 3, 64, 3, 120_000), new RandomPlayback.Limits(10, 2, 1000, 0, 3, 120_000))) {
            try (var random = new RandomPlayback(settings, storage, p -> 60.0, limits)) {
                var page = ready(random, "", 10); assertEquals("failed", page.state()); assertTrue(page.entries().isEmpty());
                assertFalse(page.message().contains(temp.toString()));
            }
        }
    }
    @Test void cachedSnapshotsRevalidateFilesAndExplicitRefreshSeesAddedFiles() throws Exception {
        video("A/old.mp4"); AtomicInteger calls = new AtomicInteger();
        try (var random = new RandomPlayback(settings, storage, p -> { calls.incrementAndGet(); return 60.0; }, defaults)) {
            assertEquals(1, ready(random, "A", 10).entries().size());
            assertEquals(1, ready(random, "A", 20).entries().size()); assertEquals(1, calls.get());
            Files.delete(settings.mediaRoot().resolve("A/old.mp4")); video("A/new.mp4");
            assertTrue(ready(random, "A", 10).entries().isEmpty());
            var initial = random.request("A", 10, null, true); RandomPlayback.Page page = initial;
            for (int i = 0; i < 100 && page.state().equals("preparing"); i++) { Thread.sleep(5); page = random.request("A", 10, initial.query(), false); }
            assertEquals(List.of("A/new.mp4"), page.entries().stream().map(RandomPlayback.Item::path).toList());
        }
    }
    @Test void pendingWorkIsNonblockingBoundedAndCancellationPreventsLatePublication() throws Exception {
        video("clip.mp4"); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var random = new RandomPlayback(settings, storage, p -> {
            entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); } return 60.0;
        }, defaults)) {
            var first = random.request("", 10, null, false); assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals("preparing", random.request("", 10, first.query(), false).state());
            var second = random.request("", 20, null, false); random.request("", 30, null, false);
            assertThrows(RejectedExecutionException.class, () -> random.request("", 40, null, false));
            random.cancel("", 10, first.query()); release.countDown();
            assertThrows(DirectoryBrowser.ExpiredCursor.class, () -> random.request("", 10, first.query(), false));
            random.cancel("", 20, second.query());
        } finally { release.countDown(); }
    }
    @Test void noMatchingDurationsAndMissingStorageAreHonestRecoverableStates() throws Exception {
        video("unknown.mp4");
        try (var random = new RandomPlayback(settings, storage, p -> null, defaults)) {
            var empty = ready(random, "", 10); assertEquals("ready", empty.state()); assertTrue(empty.entries().isEmpty()); assertFalse(empty.message().isBlank());
            var missing = ready(random, "missing", 10); assertEquals("failed", missing.state()); assertTrue(missing.entries().isEmpty());
        }
    }
    @Test void httpEndpointsValidateScopeAndTimeAndCancelWithoutCachingOrLeakingPaths() throws Exception {
        video("A/clip.mp4");
        try (var random = new RandomPlayback(settings, storage, p -> 60.0, defaults)) {
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new RandomPlaybackController(random))
                    .setControllerAdvice(new WebConfiguration.Errors()).addFilters(new WebConfiguration(settings).headers()).build();
            var page = ready(random, "A", 10);
            mvc.perform(get("/api/random-playback").param("path", "A").param("minutes", "10").param("query", page.query()))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.entries[0].path").value("A/clip.mp4"));
            mvc.perform(get("/api/random-playback").param("minutes", "15")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/random-playback").param("minutes", "10").param("path", "../outside")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/random-playback").param("minutes", "20").param("path", "A").param("query", page.query())).andExpect(status().isGone());
            mvc.perform(delete("/api/random-playback").param("minutes", "10").param("path", "A").param("query", page.query())).andExpect(status().isNoContent());
        }
    }
    @Test void totalPreparationDeadlineDoesNotPublishAPartialPlaylist() throws Exception {
        video("clip.mp4");
        var limits = new RandomPlayback.Limits(10, 2, 100, 5, 10, 10);
        try (var random = new RandomPlayback(settings, storage, p -> {
            try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); } return 60.0;
        }, limits)) {
            var page = ready(random, "", 10); assertEquals("failed", page.state()); assertTrue(page.entries().isEmpty());
        }
    }
    @Test void completedUnclaimedQueriesAreEvictedBeforeRejectingNewPlayback() throws Exception {
        video("clip.mp4");
        try (var random = new RandomPlayback(settings, storage, p -> 60.0, defaults)) {
            for (int i = 0; i < settings.maxBrowseSessions() + 3; i++) assertEquals("ready", ready(random, "", 10).state());
        }
    }
}
