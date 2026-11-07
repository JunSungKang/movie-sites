package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class PresentationTests {
    @TempDir Path temp;

    @Test void allPackagedThemesCanBeSelectedAndLoaded() throws Exception {
        var json = new JsonMapper();
        try (var input = new ClassPathResource("static/themes/catalog.json").getInputStream()) {
            var catalog = json.readTree(input);
            assertEquals(19, catalog.path("themes").size());
            for (var entry : catalog.path("themes")) {
                var service = new PresentationService(entry.path("id").asText(), "{}");
                assertEquals(entry.path("id").asText(), service.theme().id());
                String base = "static/themes/" + service.theme().directory() + "/";
                for (String asset : new String[]{"theme.json", "theme.css", "assets/hero.svg", "assets/preview.svg"})
                    assertTrue(new ClassPathResource(base + asset).exists(), base + asset);
            }
        }
    }

    @Test void refusesUnknownAndPathLikeSelections() {
        for (String value : new String[]{"", "unknown", "../child-toy", "20s/soft-minimal", "CHILD-TOY", " child-toy", "https://example.org/theme.css"})
            assertThrows(IllegalArgumentException.class, () -> new PresentationService(value, "{}"));
    }

    @Test void collectionLabelsAreOptionalLiteralTextAndNeverRenameFiles() throws Exception {
        var settings = new Settings(temp.resolve("media"), temp.resolve("data"), 120, 48, 8, 120, 4, 10_737_418_240L, 0, "ffmpeg", "ffprobe");
        Files.createDirectories(settings.mediaRoot().resolve("Shorts"));
        var presentation = new PresentationService("20s-soft-minimal", "{\"Shorts\":\"Short videos (under 5 minutes)\"}");
        try (var storage = new Storage(settings)) {
            var mvc = MockMvcBuilders.standaloneSetup(new ApiController(settings, storage, null, null, null, presentation)).build();
            mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.theme.id").value("20s-soft-minimal"))
                .andExpect(jsonPath("$.theme.directory").value("20s/soft-minimal"))
                .andExpect(jsonPath("$.folderLabels.Shorts").value("Short videos (under 5 minutes)"));
            assertTrue(Files.isDirectory(settings.mediaRoot().resolve("Shorts")));
        }
        assertTrue(new PresentationService("child-toy", "{}").folderLabels().isEmpty());
    }

    @Test void rejectsMalformedOrUnboundedLabels() {
        for (String value : new String[]{"null", "[]", "{", "{\"a\":5}", "{\"a/b\":\"text\"}", "{\"a\":\"\"}", "{\"a\":\"\\n\"}", " ".repeat(8193)})
            assertThrows(IllegalArgumentException.class, () -> new PresentationService("child-toy", value), value);
    }
}
