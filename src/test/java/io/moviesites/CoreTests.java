package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreTests {
    @TempDir Path temp;
    Settings settings(int page){return new Settings(temp.resolve("media"),temp.resolve("data"),120,page,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");}
    Storage storage(Settings s)throws Exception{Files.createDirectories(s.mediaRoot());return new Storage(s);}
    @Test void validatesExternalRootsAndLimits(){
        assertThrows(IllegalArgumentException.class,()->new Settings(temp,temp.resolve("data"),120,48,8,120,4,10000000,0,"ffmpeg","ffprobe"));
        assertThrows(IllegalArgumentException.class,()->new Settings(temp.resolve("media"),temp.resolve("data"),10,48,8,120,4,10000000,0,"ffmpeg","ffprobe"));
    }
    @Test void rejectsTraversalAndAbsolutePaths()throws Exception{
        var storage=storage(settings(48));
        for(String value:List.of("../secret.mp4","nested/../../secret.mp4","/etc/passwd","folder\\secret.mp4","a\0b"))assertThrows(IllegalArgumentException.class,()->storage.normalize(value));
        assertEquals("가족/여행",storage.normalize("가족/여행"));
    }
    @Test void securelyOpensRegularFilesAndRejectsLinks()throws Exception{
        var s=settings(48);var storage=storage(s);Files.writeString(s.mediaRoot().resolve("video.mp4"),"video bytes");Files.writeString(temp.resolve("outside.mp4"),"private");Files.createSymbolicLink(s.mediaRoot().resolve("link.mp4"),temp.resolve("outside.mp4"));
        try(var channel=storage.open("video.mp4")){var bytes=ByteBuffer.allocate(20);channel.read(bytes);assertEquals("video bytes",new String(bytes.array(),0,bytes.position()));}
        assertThrows(Exception.class,()->storage.open("link.mp4"));
        Files.createSymbolicLink(s.mediaRoot().resolve("linked"),temp);
        assertThrows(Exception.class,()->storage.open("linked/outside.mp4"));
    }
    @Test void listsOnlyImmediateChildrenAndPreservesUnicode()throws Exception{
        var s=settings(48);var storage=storage(s);Files.createDirectories(s.mediaRoot().resolve("가족/여행"));Files.writeString(s.mediaRoot().resolve("가족/여행/hidden.mp4"),"x");Files.writeString(s.mediaRoot().resolve("sample.mp4"),"x");Files.writeString(s.mediaRoot().resolve(".gitkeep"),"");
        try(var browser=new BrowserCloseable(new DirectoryBrowser(s,storage))){var result=browser.value.browse("",null);assertEquals(Set.of("가족","sample.mp4"),new HashSet<>(result.entries().stream().map(DirectoryBrowser.Entry::name).toList()));assertNull(result.nextCursor());}
    }
    @Test void givesEachBatchADistinctCursorAndRejectsReplay()throws Exception{
        var s=settings(2);var storage=storage(s);for(int i=0;i<7;i++)Files.writeString(s.mediaRoot().resolve("clip-"+i+".mp4"),"x");
        var browser=new DirectoryBrowser(s,storage);
        try{
            Set<String> seen=new HashSet<>();var page=browser.browse("",null);String firstCursor=page.nextCursor();seen.addAll(page.entries().stream().map(DirectoryBrowser.Entry::name).toList());
            var second=browser.browse("",firstCursor);assertNotEquals(firstCursor,second.nextCursor());seen.addAll(second.entries().stream().map(DirectoryBrowser.Entry::name).toList());
            while(second.nextCursor()!=null){second=browser.browse("",second.nextCursor());seen.addAll(second.entries().stream().map(DirectoryBrowser.Entry::name).toList());}
            assertEquals(7,seen.size());assertThrows(DirectoryBrowser.ExpiredCursor.class,()->browser.browse("",firstCursor));
        }finally{browser.close();}
    }
    @Test void rejectsCursorUsedForAnotherDirectory()throws Exception{
        var s=settings(1);var storage=storage(s);Files.writeString(s.mediaRoot().resolve("a.mp4"),"x");Files.writeString(s.mediaRoot().resolve("b.mp4"),"x");Files.createDirectory(s.mediaRoot().resolve("other"));var browser=new DirectoryBrowser(s,storage);
        try{String cursor=browser.browse("",null).nextCursor();assertThrows(DirectoryBrowser.ExpiredCursor.class,()->browser.browse("other",cursor));}finally{browser.close();}
    }
    @Test void rangeSupportsSeekSuffixOpenEndAndRejectsInvalidValues(){
        assertEquals(new ByteRange(0,99,false),ByteRange.parse(null,100));
        assertEquals(new ByteRange(10,19,true),ByteRange.parse("bytes=10-19",100));
        assertEquals(new ByteRange(95,99,true),ByteRange.parse("bytes=-5",100));
        assertEquals(new ByteRange(90,99,true),ByteRange.parse("bytes=90-",100));
        assertEquals(new ByteRange(90,99,true),ByteRange.parse("bytes=90-1000",100));
        for(String value:List.of("bytes=100-","bytes=20-10","bytes=-0","bytes=0-1,3-4","bytes=99999999999999999999999-","garbage"))assertThrows(IllegalArgumentException.class,()->ByteRange.parse(value,100));
        assertThrows(IllegalArgumentException.class,()->ByteRange.parse("bytes=0-1",0));
    }
    @Test void largeDirectoryReturnsOnlyABoundedBatch()throws Exception{
        var s=settings(48);var storage=storage(s);
        for(int i=0;i<10000;i++)Files.createFile(s.mediaRoot().resolve("video-"+i+".mp4"));
        var browser=new DirectoryBrowser(s,storage);
        try{var page=browser.browse("",null);assertEquals(48,page.entries().size());assertNotNull(page.nextCursor());}finally{browser.close();}
    }
    @Test void playbackAuthorizationSurvivesJobEvictionAndRejectsForgery()throws Exception{
        var s=settings(48);var storage=storage(s);Files.writeString(s.mediaRoot().resolve("video.mp4"),"bytes");
        var media=new MediaService(s,storage);
        try{String version=storage.version("video.mp4");String token=media.playbackToken("video.mp4",version,true);
            assertNull(media.prepared("video.mp4",version,token));
            assertThrows(AccessDeniedException.class,()->media.prepared("video.mp4",version,token.replace("original.","converted.")));
            Files.writeString(s.mediaRoot().resolve("video.mp4"),"changed bytes");
            assertThrows(IllegalStateException.class,()->media.prepared("video.mp4",version,token));
        }finally{media.close();}
    }
    @Test void probesFirstAudioAndIgnoresAttachedCoverVideo()throws Exception{
        var base=settings(48);Files.createDirectories(base.mediaRoot());Files.writeString(base.mediaRoot().resolve("video.mp4"),"media");
        Path probe=temp.resolve("probe.sh");
        Files.writeString(probe,"#!/bin/sh\nprintf '%s' '{\"format\":{\"duration\":\"5\"},\"streams\":[{\"index\":0,\"codec_type\":\"video\",\"codec_name\":\"mjpeg\",\"width\":100,\"height\":100,\"disposition\":{\"attached_pic\":1}},{\"index\":1,\"codec_type\":\"video\",\"codec_name\":\"h264\",\"pix_fmt\":\"yuv420p\",\"width\":1280,\"height\":720},{\"index\":2,\"codec_type\":\"audio\",\"codec_name\":\"ac3\"},{\"index\":3,\"codec_type\":\"audio\",\"codec_name\":\"aac\"}]}'\n");
        assertTrue(probe.toFile().setExecutable(true));
        var s=new Settings(base.mediaRoot(),base.dataRoot(),120,48,8,120,4,base.maxCacheBytes(),0,"/bin/false",probe.toString());var media=new MediaService(s,new Storage(s));
        try{MediaService.Info info=null;for(int i=0;i<100;i++){info=media.inspect("video.mp4",false,false);if(info.state().equals("inspected")||info.state().equals("failed"))break;Thread.sleep(20);}assertNotNull(info);assertEquals("inspected",info.state());assertEquals(1280,info.width());assertEquals("converted",info.playback());}finally{media.close();}
    }
    @Test void sourceVersionChangesWhenFileChanges()throws Exception{
        var s=settings(48);var storage=storage(s);Path p=s.mediaRoot().resolve("a.mp4");Files.writeString(p,"one");String first=storage.version("a.mp4");Files.writeString(p,"different length");assertNotEquals(first,storage.version("a.mp4"));
    }
    static class BrowserCloseable implements AutoCloseable{final DirectoryBrowser value;BrowserCloseable(DirectoryBrowser value){this.value=value;}public void close(){value.close();}}
}
