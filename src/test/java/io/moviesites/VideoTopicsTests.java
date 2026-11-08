package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class VideoTopicsTests {
    @TempDir Path temp;
    Storage storage; Settings settings; VideoTopics topics;
    @BeforeEach void setup() throws Exception {
        settings = new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");
        Files.createDirectories(settings.mediaRoot()); storage = new Storage(settings); topics = new VideoTopics(storage);
    }
    @AfterEach void close() throws Exception { storage.close(); }
    @Test void titleSuggestionsAreConservativeAndMarkedRatherThanClaimedAsContentAnalysis() {
        var food = topics.read("야채 삼총사.mp4"); assertTrue(food.tags().contains("식습관교육")); assertEquals("filename",food.source());
        assertEquals(List.of("배변교육"),topics.read("응가대장.mp4").tags());
        assertEquals("none",topics.read("episode-001.mp4").source());
    }
    @Test void explicitTagsOverrideRecommendationsIncludingIntentionalEmptyLabels() throws Exception {
        Path sidecar = settings.mediaRoot().resolve("야채.mp4.json"); Files.writeString(sidecar,"{\"tags\":[\"Custom topic\",\"Custom topic\",\"두 번째 주제\"]}");
        var value=topics.read("야채.mp4"); assertEquals("metadata",value.source()); assertEquals(List.of("Custom topic","두 번째 주제"),value.tags());
        Files.writeString(sidecar,"{\"tags\":[]}"); assertTrue(topics.read("야채.mp4").tags().isEmpty()); assertEquals("metadata",topics.read("야채.mp4").source());
    }
    @Test void malformedOversizedAndSymlinkSidecarsCannotEscapeOrBreakTheList() throws Exception {
        Path sidecar=settings.mediaRoot().resolve("야채.mp4.json");
        for(String text:List.of("{","null","[]","{\"tags\":[1]}","{\"tags\":[\"\"]}","{\"tags\":[\"x\\n\"]}","{\"tags\":[\""+"x".repeat(41)+"\"]}"," ".repeat(8193))) {
            Files.writeString(sidecar,text); var result=topics.read("야채.mp4"); assertTrue(result.invalidMetadata(),text); assertTrue(result.tags().contains("식습관교육"));
        }
        Files.delete(sidecar); Path outside=temp.resolve("outside.json");Files.writeString(outside,"{\"tags\":[\"private\"]}");Files.createSymbolicLink(sidecar,outside);
        assertFalse(topics.read("야채.mp4").tags().contains("private"));
        assertFalse(topics.read("../outside").tags().contains("private"));
    }
}
