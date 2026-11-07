package io.moviesites;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;

/** An explicit, small image in this directory; never discovers descendant videos. */
@Service
public class FolderCoverService {
    static final int MAX_BYTES = 512 * 1024;
    static final long MAX_PIXELS = 1280L * 720;
    private final Storage storage;

    public FolderCoverService(Storage storage) { this.storage = storage; }

    public byte[] read(String folder) throws IOException {
        String path = storage.normalize(folder);
        String file = path.isEmpty() ? ".folder.jpg" : path + "/.folder.jpg";
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
