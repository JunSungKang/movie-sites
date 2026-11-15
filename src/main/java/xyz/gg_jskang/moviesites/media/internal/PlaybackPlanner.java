package xyz.gg_jskang.moviesites.media.internal;
import java.util.Locale;
final class PlaybackPlanner {
    PlaybackPlan choose(String path,MediaProbe probe){
        if(probe.copyCompatible()&&probe.videoCount()==1&&probe.audioCount()<=1&&path.toLowerCase(Locale.ROOT).endsWith(".mp4"))return new PlaybackPlan.Original();
        return probe.copyCompatible()?new PlaybackPlan.Remux():new PlaybackPlan.Transcode();
    }
}
