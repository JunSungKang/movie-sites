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
        topics = new VideoTopics(storage, "kids-education", new UiLocale("ko"));
        var food = topics.read("야채 삼총사.mp4"); assertTrue(food.tags().contains("식습관교육")); assertEquals("filename",food.source());
        assertEquals(List.of("배변교육"),topics.read("응가대장.mp4").tags());
        assertEquals("none",topics.read("episode-001.mp4").source());
    }
    @Test void suggestedLabelsFollowUiLocaleWhileManualTagsStayUnchanged() throws Exception {
        var english = new VideoTopics(storage, "kids-education", new UiLocale("en"));
        assertEquals(List.of("Healthy eating"), english.read("야채.mp4").tags());
        assertEquals("filename", english.read("야채.mp4").source());
        Files.writeString(settings.mediaRoot().resolve("야채.mp4.json"), "{\"tags\":[\"직접 지정\"]}");
        assertEquals(List.of("직접 지정"), english.read("야채.mp4").tags());
    }
    @Test void defaultProfileDoesNotInferEducationFromUnrelatedTitles() {
        var result = topics.read("야채 삼총사.mp4");
        assertEquals(List.of(), result.tags()); assertEquals("none", result.source());
        assertFalse(result.invalidMetadata());
    }
    @Test void unknownProfileIsRejectedAtStartupRatherThanLoadingExternalResources() {
        for (String profile : List.of("unknown", "../kids-education", "kids-education.json", "", "NONE"))
            assertThrows(IllegalArgumentException.class, () -> new VideoTopics(storage, profile));
    }
    @Test void explicitTagsOverrideRecommendationsIncludingIntentionalEmptyLabels() throws Exception {
        Path sidecar = settings.mediaRoot().resolve("야채.mp4.json"); Files.writeString(sidecar,"{\"tags\":[\"Custom topic\",\"Custom topic\",\"두 번째 주제\"]}");
        for (String profile : List.of("none", "kids-education")) {
            var selected = new VideoTopics(storage, profile);
            var value = selected.read("야채.mp4"); assertEquals("metadata", value.source());
            assertEquals(List.of("Custom topic", "두 번째 주제"), value.tags());
        }
        Files.writeString(sidecar,"{\"tags\":[]}");
        for (String profile : List.of("none", "kids-education")) {
            var value = new VideoTopics(storage, profile).read("야채.mp4");
            assertTrue(value.tags().isEmpty()); assertEquals("metadata", value.source());
        }
    }
    @Test void malformedMetadataInNeutralProfileIsReportedWithoutInferredTags() throws Exception {
        Files.writeString(settings.mediaRoot().resolve("야채.mp4.json"), "{");
        var value = topics.read("야채.mp4");
        assertTrue(value.invalidMetadata()); assertEquals(List.of(), value.tags()); assertEquals("none", value.source());
    }
    @Test void malformedOversizedAndSymlinkSidecarsCannotEscapeOrBreakTheList() throws Exception {
        topics = new VideoTopics(storage, "kids-education", new UiLocale("ko"));
        Path sidecar=settings.mediaRoot().resolve("야채.mp4.json");
        for(String text:List.of("{","null","[]","{\"tags\":[1]}","{\"tags\":[\"\"]}","{\"tags\":[\"x\\n\"]}","{\"tags\":[\""+"x".repeat(41)+"\"]}"," ".repeat(8193))) {
            Files.writeString(sidecar,text); var result=topics.read("야채.mp4"); assertTrue(result.invalidMetadata(),text); assertTrue(result.tags().contains("식습관교육"));
        }
        Files.delete(sidecar); Path outside=temp.resolve("outside.json");Files.writeString(outside,"{\"tags\":[\"private\"]}");Files.createSymbolicLink(sidecar,outside);
        assertFalse(topics.read("야채.mp4").tags().contains("private"));
        assertFalse(topics.read("../outside").tags().contains("private"));
    }
}
