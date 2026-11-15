package xyz.gg_jskang.moviesites.support;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
public final class TestImages {
    private TestImages() {}
    public static byte[] jpeg(int width,int height)throws Exception {
        var bytes=new ByteArrayOutputStream();try(var output=new MemoryCacheImageOutputStream(bytes)){if(!ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"jpeg",output))throw new IllegalStateException("No JPEG writer");}return bytes.toByteArray();
    }
}
