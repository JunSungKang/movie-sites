package xyz.gg_jskang.moviesites.support;
import java.util.Locale;
import java.util.Set;
public final class MediaFiles {
    private static final Set<String> EXTENSIONS=Set.of("mp4","m4v","mov","mkv","webm","avi","mpeg","mpg","mts","m2ts","ts","ogv","wmv","flv","3gp","3g2","mxf","vob","av1");
    private MediaFiles() {}
    public static boolean isVideo(String name) {int dot=name.lastIndexOf('.');return dot>=0&&EXTENSIONS.contains(name.substring(dot+1).toLowerCase(Locale.ROOT));}
}
