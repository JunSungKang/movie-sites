package xyz.gg_jskang.moviesites.media.internal;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PlaybackPolicyTests {
    MediaProbe probe(String video,String audio,String transfer,int videos,int audios){return new MediaProbe(5.0,1920,1080,video,audio,"yuv420p",transfer,0,audios==0?-1:1,videos,audios);}
    @Test void directRemuxAndTranscodeAreChosenFromActualCompatibility() {
        var planner=new PlaybackPlanner();var h264=probe("h264","aac","",1,1);
        assertInstanceOf(PlaybackPlan.Original.class,planner.choose("clip.MP4",h264));
        assertInstanceOf(PlaybackPlan.Remux.class,planner.choose("clip.mkv",h264));
        assertInstanceOf(PlaybackPlan.Remux.class,planner.choose("clip.mp4",probe("h264","aac","",2,1)));
        assertInstanceOf(PlaybackPlan.Transcode.class,planner.choose("clip.mp4",probe("hevc","aac","",1,1)));
        assertInstanceOf(PlaybackPlan.Transcode.class,planner.choose("clip.mp4",probe("h264","ac3","",1,1)));
    }
    @Test void transcodePreservesAspectLimitsAndToneMapsHdrOnlyWhenNeeded() {
        var strategy=new PlaybackPlan.Transcode();
        String sdr=String.join(" ",strategy.arguments(probe("hevc","aac","",1,1)));
        String hdr=String.join(" ",strategy.arguments(probe("hevc","aac","smpte2084",1,1)));
        assertTrue(sdr.contains("force_original_aspect_ratio=decrease"));assertTrue(sdr.contains("format=yuv420p"));assertFalse(sdr.contains("tonemap"));assertTrue(hdr.contains("tonemap=tonemap=hable"));
    }
    @Test void mediaTransitionsAllowEvictionAndQueueRollbackButRejectRevival() {
        var inspected=MediaJobState.QUEUED.transition(MediaJobState.ANALYZING).transition(MediaJobState.INSPECTED);
        assertEquals(inspected,inspected.transition(MediaJobState.PREPARING).transition(MediaJobState.INSPECTED));
        assertEquals(MediaJobState.INSPECTED,MediaJobState.READY.transition(MediaJobState.INSPECTED));
        assertThrows(IllegalStateException.class,()->MediaJobState.QUEUED.transition(MediaJobState.READY));
        assertThrows(IllegalStateException.class,()->MediaJobState.FAILED.transition(MediaJobState.READY));
    }
}
