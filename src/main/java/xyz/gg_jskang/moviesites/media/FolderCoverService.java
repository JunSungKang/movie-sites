package xyz.gg_jskang.moviesites.media;

import xyz.gg_jskang.moviesites.storage.MediaStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;

/** An explicit, small image in this directory; never discovers descendant videos. */
@Service
public class FolderCoverService {
    static final int MAX_BYTES = 512 * 1024;
    static final long MAX_PIXELS = 1280L * 720;
    private final MediaStore storage;

    public FolderCoverService(MediaStore storage) { this.storage = storage; }

    public byte[] read(String folder) throws IOException {
        String path = storage.normalize(folder);
        String file = path.isEmpty() ? ".folder.jpg" : path + "/.folder.jpg";
        return readImage(file);
    }

    public static void validateWidth(Integer width) {
        if (width != null && width != 320 && width != 640 && width != 960)
            throw new IllegalArgumentException("Unsupported folder cover width");
    }

    public byte[] read(String folder, Integer width) throws IOException {
        validateWidth(width);
        byte[] original = read(folder);
        if (width == null || hasExif(original)) return original;
        BufferedImage source = decode(original);
        if (source.getWidth() <= width) return original;
        int height = Math.max(1, (int) Math.round((double) source.getHeight() * width / source.getWidth()));
        var resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        var bytes = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("JPEG").next();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            var parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(.8f);
            writer.write(null, new IIOImage(resized, null, null), parameters);
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Cannot decode folder cover");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    // Keep EXIF-bearing originals intact so a derivative cannot lose orientation metadata.
    private static boolean hasExif(byte[] bytes) {
        for (int offset = 2; offset + 4 <= bytes.length;) {
            if ((bytes[offset++] & 255) != 255) return false;
            int marker = bytes[offset++] & 255;
            if (marker == 255) { offset--; continue; }
            if (marker == 218 || marker == 217) return false;
            if (marker == 1 || marker >= 208 && marker <= 215) continue;
            int length = (bytes[offset] & 255) << 8 | bytes[offset + 1] & 255;
            if (length < 2 || length > bytes.length - offset) return false;
            if (marker == 225 && length >= 8 && bytes[offset + 2] == 'E' && bytes[offset + 3] == 'x'
                    && bytes[offset + 4] == 'i' && bytes[offset + 5] == 'f' && bytes[offset + 6] == 0 && bytes[offset + 7] == 0) return true;
            offset += length;
        }
        return false;
    }

    public byte[] readImage(String file) throws IOException {
        byte[] bytes;
        try (var channel = storage.open(file); var input = Channels.newInputStream(channel)) {
            if (channel.size() > MAX_BYTES) throw new IllegalArgumentException("Folder cover is too large");
            bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Folder cover is too large");
        }
        // Explicit memory cache: ImageIO must not create temporary disk files.
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Folder cover must be JPEG");
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("JPEG")) throw new IllegalArgumentException("Folder cover must be JPEG");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 1280 || height > 1280 || (long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Folder cover dimensions are too large");
            } finally { reader.dispose(); }
        }
        return bytes;
    }
}
