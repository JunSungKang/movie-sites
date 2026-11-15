package xyz.gg_jskang.moviesites.media;
import xyz.gg_jskang.moviesites.system.web.ApiExceptionHandler;

import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.configuration.WebConfiguration;
import xyz.gg_jskang.moviesites.media.FolderCoverService;
import xyz.gg_jskang.moviesites.presentation.PresentationService;
import xyz.gg_jskang.moviesites.media.web.MediaController;
import xyz.gg_jskang.moviesites.system.web.SystemController;
import xyz.gg_jskang.moviesites.storage.TestStorage;
import xyz.gg_jskang.moviesites.support.TestImages;

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
    TestStorage storage;
    Settings settings;
    FolderCoverService covers;

    @BeforeEach void setup() throws Exception {
        settings = new Settings(temp.resolve("media"), temp.resolve("data"), 120, 48, 8, 120, 4, 10_737_418_240L, 0, "ffmpeg", "ffprobe");
        Files.createDirectories(settings.mediaRoot());
        storage = new TestStorage(settings);
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
    Path folder() throws Exception { return Files.createDirectories(settings.mediaRoot().resolve("컬렉션")); }

    @Test void servesOnlyTheRequestedFoldersExplicitJpeg() throws Exception {
        byte[] expected = jpeg(20, 10);
        Files.write(folder().resolve(".folder.jpg"), expected);
        var mvc = MockMvcBuilders.standaloneSetup(new MediaController(storage,covers,null))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/media/folder-cover").param("path", "컬렉션"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private"))).andExpect(content().bytes(expected));
        mvc.perform(get("/api/media/folder-cover").param("path", "../outside"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/media/folder-cover").param("path", "없는 폴더"))
                .andExpect(status().isNotFound());
    }
    @Test void doesNotSearchChildrenWhenCoverIsMissing() throws Exception {
        Path nested = Files.createDirectories(folder().resolve("아래"));
        Files.write(nested.resolve(".folder.jpg"), jpeg(20, 10));
        assertThrows(NoSuchFileException.class, () -> covers.read("컬렉션"));
    }
    @Test void refusesSymbolicLinksAtFileAndDirectoryBoundaries() throws Exception {
        Path outside = temp.resolve("outside.jpg"); Files.write(outside, jpeg(20, 10));
        Files.createSymbolicLink(folder().resolve(".folder.jpg"), outside);
        assertThrows(Exception.class, () -> covers.read("컬렉션"));
        Files.write(temp.resolve(".folder.jpg"), jpeg(20, 10));
        Files.createSymbolicLink(settings.mediaRoot().resolve("linked"), temp);
        assertThrows(Exception.class, () -> covers.read("linked"));
    }
    @Test void rejectsOversizedOrNonJpegFiles() throws Exception {
        Path cover = folder().resolve(".folder.jpg");
        Files.write(cover, new byte[FolderCoverService.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션"));
        Files.writeString(cover, "This is not a JPEG");
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션"));
        var png = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(png)) {
            assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output));
        }
        Files.write(cover, png.toByteArray());
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션"));
    }
    @Test void boundsDecodedDimensionsBeforeReturningImage() throws Exception {
        Path cover = folder().resolve(".folder.jpg");
        Files.write(cover, jpeg(1280, 721));
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션"));
        Files.write(cover, jpeg(1281, 1));
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션"));
    }
    @Test void requestedVariantPreservesAspectAndOriginalStorage() throws Exception {
        Path file = folder().resolve(".folder.jpg");
        byte[] original = jpeg(960, 540); Files.write(file, original);
        var modified = Files.getLastModifiedTime(file); String version = storage.version("컬렉션/.folder.jpg");
        byte[] variant = covers.read("컬렉션", 640);
        BufferedImage image = TestImages.decode(variant);
        assertEquals(640, image.getWidth()); assertEquals(360, image.getHeight());
        assertTrue(variant.length < original.length);
        assertArrayEquals(original, covers.read("컬렉션")); assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(modified, Files.getLastModifiedTime(file)); assertEquals(version, storage.version("컬렉션/.folder.jpg"));
    }
    @Test void smallerImagesAndExifMetadataArePreservedWithoutUpscaling() throws Exception {
        Path file = folder().resolve(".folder.jpg"); byte[] small = jpeg(20, 10); Files.write(file, small);
        assertArrayEquals(small, covers.read("컬렉션", 320));
        byte[] original = jpeg(960, 540);
        byte[] exif = { (byte) 255, (byte) 225, 0, 34, 'E', 'x', 'i', 'f', 0, 0,
            'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0 };
        var output = new ByteArrayOutputStream(); output.write(original, 0, 2); output.write(exif); output.write(original, 2, original.length - 2);
        byte[] oriented = output.toByteArray(); Files.write(file, oriented);
        assertArrayEquals(oriented, covers.read("컬렉션", 320));
    }
    @Test void variantsKeepInputAndWidthBoundaries() throws Exception {
        Path file = folder().resolve(".folder.jpg"); Files.write(file, jpeg(960, 540));
        var mvc = MockMvcBuilders.standaloneSetup(new MediaController(storage,covers,null))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        for (String width : new String[]{"0", "-1", "1", "639", "1280", "2147483647", "not-a-number"})
            mvc.perform(get("/api/media/folder-cover").param("path", "컬렉션").param("w", width))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/media/folder-cover").param("path", "../outside").param("w", "640"))
            .andExpect(status().isBadRequest());
        Files.write(file, new byte[FolderCoverService.MAX_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션", 640));
        Files.write(file, jpeg(1280, 721));
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션", 640));
        Files.writeString(file, "not JPEG");
        assertThrows(IllegalArgumentException.class, () -> covers.read("컬렉션", 640));
        Files.delete(file); Path outside = temp.resolve("outside-variant.jpg"); Files.write(outside, jpeg(960, 540));
        Files.createSymbolicLink(file, outside);
        assertThrows(Exception.class, () -> covers.read("컬렉션", 640));
    }
}
