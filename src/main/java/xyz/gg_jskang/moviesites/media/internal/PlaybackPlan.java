package xyz.gg_jskang.moviesites.media.internal;
import java.util.List;
import java.util.Set;
/** Strategy for direct, remuxed or transcoded playback; original files are never modified. */
sealed interface PlaybackPlan permits PlaybackPlan.Original,PlaybackPlan.Remux,PlaybackPlan.Transcode {
    boolean direct();
    List<String> arguments(MediaProbe probe);
    record Original() implements PlaybackPlan {
        public boolean direct(){return true;}
        public List<String> arguments(MediaProbe probe){return List.of();}
    }
    record Remux() implements PlaybackPlan {
        public boolean direct(){return false;}
        public List<String> arguments(MediaProbe probe){return List.of("-c","copy");}
    }
    record Transcode() implements PlaybackPlan {
        public boolean direct(){return false;}
        public List<String> arguments(MediaProbe probe){
            String filter="scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p";
            if(Set.of("smpte2084","arib-std-b67").contains(probe.transfer()))filter="zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable,zscale=t=bt709:m=bt709:r=tv,"+filter;
            return List.of("-vf",filter,"-c:v","libx264","-preset","veryfast","-crf","22","-threads","2","-c:a","aac","-b:a","160k","-ac","2");
        }
    }
}
