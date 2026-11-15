package xyz.gg_jskang.moviesites.storage;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributes;
/** Read-only access to mounted original media. There are no write/delete methods. */
public interface MediaStore {
    String normalize(String input);
    SecureDirectoryStream<Path> directory(String input) throws IOException;
    BasicFileAttributes attributes(String input) throws IOException;
    SeekableByteChannel open(String input) throws IOException;
    Path processInput(String input) throws IOException;
    String version(String path) throws IOException;
    String rootId();
}
