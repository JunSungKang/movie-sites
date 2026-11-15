package xyz.gg_jskang.moviesites.media.internal;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import java.util.Base64;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.MediaStore;
/** Signed parameters protect source/version consistency, not user identity. Keys are restart-local. */
final class PlaybackTokens {
    private final Settings settings;private final MediaStore storage;
    private final SecretKey playbackKey=createPlaybackKey();
    PlaybackTokens(Settings settings,MediaStore storage){this.settings=settings;this.storage=storage;}
    private static SecretKey createPlaybackKey(){try{return KeyGenerator.getInstance("HmacSHA256").generateKey();}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    String sign(String path,String version,boolean original){
        String mode=original?"original":"converted";
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(playbackKey);return mode+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((storage.normalize(path)+"\0"+version+"\0"+mode).getBytes(StandardCharsets.UTF_8)));}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public Path prepared(String path,String version,String token) throws IOException {
        if(!storage.version(path).equals(version))throw new IllegalStateException("Source changed");
        boolean original=token!=null&&token.startsWith("original.");
        String expected=sign(path,version,original);
        if(token==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),token.getBytes(StandardCharsets.US_ASCII)))throw new AccessDeniedException("Invalid playback authorization");
        if(original)return null;
        Path file=settings.dataRoot().resolve("playback").resolve(version+".mp4");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Playback cache expired");
        return file;
    }
}
