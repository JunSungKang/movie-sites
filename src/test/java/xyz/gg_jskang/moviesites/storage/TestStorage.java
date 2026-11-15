package xyz.gg_jskang.moviesites.storage;
import java.io.IOException;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.internal.NioMediaStore;
/** Test-owned lifetime; production injects independent read/write boundaries. */
public class TestStorage extends NioMediaStore implements AutoCloseable {
    private final GeneratedDataWorkspace workspace;
    public TestStorage(Settings settings) throws IOException {this(settings,new GeneratedDataWorkspace(settings));}
    private TestStorage(Settings settings,GeneratedDataWorkspace workspace) {super(settings,workspace);this.workspace=workspace;}
    public GeneratedDataWorkspace workspace() {return workspace;}
    @Override public void close() throws IOException {workspace.close();}
}
