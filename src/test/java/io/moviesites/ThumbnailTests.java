package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ThumbnailTests {
    @TempDir Path temp;
    Settings settings; Storage storage; MediaService media;
    @BeforeEach void setup() throws Exception {
        settings=new Settings(temp.resolve("media"),temp.resolve("data"),120,48,8,120,4,10_737_418_240L,0,"/bin/false","/bin/false");
        Files.createDirectories(settings.mediaRoot());storage=new Storage(settings);media=new MediaService(settings,storage);
        Files.writeString(settings.mediaRoot().resolve("clip.mp4"),"video");
    }
    @AfterEach void close() throws Exception { media.close();storage.close(); }
    byte[] jpeg(int width,int height) throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var output=new MemoryCacheImageOutputStream(bytes)){assertTrue(ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"jpeg",output));}
        return bytes.toByteArray();
    }
    @Test void selectedThumbnailWinsAndHasIndependentVersionWithoutChangingPlayback() throws Exception {
        String original=storage.version("clip.mp4");byte[] generated=jpeg(20,10),selected=jpeg(30,20);
        Files.write(settings.dataRoot().resolve("thumbnails").resolve(original+".jpg"),generated);
        Files.write(settings.mediaRoot().resolve("clip.mp4.jpg"),selected);
        String v=media.selectedThumbnailVersion("clip.mp4");assertNotNull(v);assertNotEquals(original,v);
        assertArrayEquals(selected,media.thumbnailResource("clip.mp4",v).getContentAsByteArray());
        var info=media.inspect("clip.mp4",false,false);assertEquals("ready",info.thumbnailState());assertEquals(v,info.thumbnailVersion());
        Files.write(settings.mediaRoot().resolve("clip.mp4.jpg"),jpeg(40,30));
        assertNotEquals(v,media.selectedThumbnailVersion("clip.mp4"));assertEquals(original,storage.version("clip.mp4"));
        assertThrows(NoSuchFileException.class,()->media.thumbnailResource("clip.mp4",v).getContentAsByteArray());
        Files.delete(settings.mediaRoot().resolve("clip.mp4.jpg"));assertArrayEquals(generated,media.thumbnailResource("clip.mp4",original).getContentAsByteArray());
    }
    @Test void invalidOrOversizedSidecarsFallBackAndLinksCannotEscape() throws Exception {
        String version=storage.version("clip.mp4");byte[] fallback=jpeg(20,10);
        Files.write(settings.dataRoot().resolve("thumbnails").resolve(version+".jpg"),fallback);
        Path sidecar=settings.mediaRoot().resolve("clip.mp4.jpg");
        for(byte[] bad:new byte[][]{new byte[FolderCoverService.MAX_BYTES+1],"not a jpeg".getBytes(),jpeg(1281,1)}){
            Files.write(sidecar,bad);assertNull(media.selectedThumbnailVersion("clip.mp4"));assertArrayEquals(fallback,media.thumbnailResource("clip.mp4",version).getContentAsByteArray());
        }
        Files.delete(sidecar);Path outside=temp.resolve("outside.jpg");Files.write(outside,jpeg(20,10));Files.createSymbolicLink(sidecar,outside);
        assertNull(media.selectedThumbnailVersion("clip.mp4"));assertArrayEquals(fallback,media.thumbnailResource("clip.mp4",version).getContentAsByteArray());
        assertThrows(IllegalArgumentException.class,()->media.thumbnailResource("../clip.mp4",version).getContentAsByteArray());
        assertThrows(NoSuchFileException.class,()->media.thumbnailResource("missing.mp4",version).getContentAsByteArray());
    }
}
