package xyz.gg_jskang.moviesites.support;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
public final class TestImages {
    private TestImages() {}
    public static byte[] jpeg(int width,int height)throws Exception {
        var bytes=new ByteArrayOutputStream();try(var output=new MemoryCacheImageOutputStream(bytes)){if(!ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"jpeg",output))throw new IllegalStateException("No JPEG writer");}return bytes.toByteArray();
    }
    public static BufferedImage decode(byte[] bytes)throws IOException {
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("No image reader");
            var reader=readers.next();
            try {reader.setInput(input,true,true);return reader.read(0);}finally{reader.dispose();}
        }
    }
}
