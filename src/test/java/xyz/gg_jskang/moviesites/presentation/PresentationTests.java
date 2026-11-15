package xyz.gg_jskang.moviesites.presentation;

import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.presentation.PresentationService;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.media.web.MediaController;
import xyz.gg_jskang.moviesites.system.web.SystemController;
import xyz.gg_jskang.moviesites.storage.TestStorage;

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
            assertTrue(catalog.path("themes").size() >= 19, "Keep the bundled baseline and allow contributed themes");
            for (var entry : catalog.path("themes")) {
                var service = new PresentationService(entry.path("id").asText(), "{}");
                assertEquals(entry.path("id").asText(), service.theme().id());
                assertTrue(service.themeColor().matches("#[a-fA-F0-9]{6}"));
                String base = "static/themes/" + service.theme().directory() + "/";
                for (String asset : new String[]{"theme.json", "theme.css", "assets/hero.svg", "assets/preview.svg"})
                    assertTrue(new ClassPathResource(base + asset).exists(), base + asset);
            }
        }
    }

    @Test void themeDisplayNamesFollowTheSelectedInterfaceLanguage() throws Exception {
        assertEquals("Midnight Stream", new PresentationService("20s-midnight-stream", "{}", new UiLocale("en")).theme().name());
        assertEquals("미드나이트 스트림", new PresentationService("20s-midnight-stream", "{}", new UiLocale("ko")).theme().name());
        assertEquals("Toy Playground", new PresentationService("child-toy", "{}", new UiLocale("en")).theme().name());
    }

    @Test void refusesUnknownAndPathLikeSelections() {
        for (String value : new String[]{"", "unknown", "../child-toy", "20s/soft-minimal", "CHILD-TOY", " child-toy", "https://example.org/theme.css"})
            assertThrows(IllegalArgumentException.class, () -> new PresentationService(value, "{}"));
    }

    @Test void collectionLabelsAreOptionalLiteralTextAndNeverRenameFiles() throws Exception {
        var settings = new Settings(temp.resolve("media"), temp.resolve("data"), 120, 48, 8, 120, 4, 10_737_418_240L, 0, "ffmpeg", "ffprobe");
        Files.createDirectories(settings.mediaRoot().resolve("Shorts"));
        var presentation = new PresentationService("20s-soft-minimal", "{\"Shorts\":\"Short videos (under 5 minutes)\"}");
        try (var storage = new TestStorage(settings)) {
            var mvc = MockMvcBuilders.standaloneSetup(new SystemController(settings,storage,presentation)).build();
            mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.locale").value("en"))
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
