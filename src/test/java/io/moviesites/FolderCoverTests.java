package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FolderCoverTests {
    @TempDir Path temp;
    Storage storage;
    Settings settings;
    FolderCoverService covers;

    @BeforeEach void setup() throws Exception {
        settings = new Settings(temp.resolve("media"), temp.resolve("data"), 120, 48, 8, 120, 4, 10_737_418_240L, 0, "ffmpeg", "ffprobe");
        Files.createDirectories(settings.mediaRoot());
        storage = new Storage(settings);
        covers = new FolderCoverService(storage);
    }
    @AfterEach void close() throws Exception { storage.close(); }
    byte[] jpeg(int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", output));
        }
        return bytes.toByteArray();
    }
    Path folder() throws Exception { return Files.createDirectories(settings.mediaRoot().resolve("친구들")); }

    @Test void servesOnlyTheRequestedFoldersExplicitJpeg() throws Exception {
        byte[] expected = jpeg(20, 10);
        Files.write(folder().resolve(".folder.jpg"), expected);
        var mvc = MockMvcBuilders.standaloneSetup(new ApiController(settings, storage, null, null, covers))
                .setControllerAdvice(new WebConfiguration.Errors()).build();
        mvc.perform(get("/api/media/folder-cover").param("path", "친구들"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(expected));
        mvc.perform(get("/api/media/folder-cover").param("path", "../outside"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/media/folder-cover").param("path", "없는 폴더"))
                .andExpect(status().isNotFound());
    }
    @Test void doesNotSearchChildrenWhenCoverIsMissing() throws Exception {
        Path nested = Files.createDirectories(folder().resolve("아래"));
        Files.write(nested.resolve(".folder.jpg"), jpeg(20, 10));
        assertThrows(NoSuchFileException.class, () -> covers.read("친구들"));
    }
    @Test void refusesSymbolicLinksAtFileAndDirectoryBoundaries() throws Exception {
        Path outside = temp.resolve("outside.jpg"); Files.write(outside, jpeg(20, 10));
        Files.createSymbolicLink(folder().resolve(".folder.jpg"), outside);
        assertThrows(Exception.class, () -> covers.read("친구들"));
        Files.write(temp.resolve(".folder.jpg"), jpeg(20, 10));
        Files.createSymbolicLink(settings.mediaRoot().resolve("linked"), temp);
        assertThrows(Exception.class, () -> covers.read("linked"));
    }
    @Test void rejectsOversizedOrNonJpegFiles() throws Exception {
        Path cover = folder().resolve(".folder.jpg");
        Files.write(cover, new byte[FolderCoverService.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> covers.read("친구들"));
        Files.writeString(cover, "This is not a JPEG");
        assertThrows(IllegalArgumentException.class, () -> covers.read("친구들"));
        var png = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(png)) {
            assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output));
        }
        Files.write(cover, png.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> covers.read("친구들"));
    }
    @Test void boundsDecodedDimensionsBeforeReturningImage() throws Exception {
        Path cover = folder().resolve(".folder.jpg");
        Files.write(cover, jpeg(1280, 721));
        assertThrows(IllegalArgumentException.class, () -> covers.read("친구들"));
        Files.write(cover, jpeg(1281, 1));
        assertThrows(IllegalArgumentException.class, () -> covers.read("친구들"));
    }
}
