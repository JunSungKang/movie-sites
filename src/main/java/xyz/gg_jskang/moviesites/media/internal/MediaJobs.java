package xyz.gg_jskang.moviesites.media.internal;

import xyz.gg_jskang.moviesites.media.MediaService.Info;
import xyz.gg_jskang.moviesites.storage.GeneratedDataWorkspace;

import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.storage.MediaStore;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.core.io.Resource;

public final class MediaJobs {
    private final Settings settings; private final MediaStore storage; private final UiLocale locale;
    private final PlaybackTokens tokens;
    private final MediaImages images;
    private final Map<String,Job> jobs=new LinkedHashMap<>(32,.75f,true);
    private final GeneratedDataWorkspace budget;
    private final MediaTools tools;
    private final PlaybackPlanner planner=new PlaybackPlanner();
    private final ThreadPoolExecutor analysis=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->daemon(r,"media-analysis"));
    private final ThreadPoolExecutor conversion=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->daemon(r,"media-conversion"));
    private static Thread daemon(Runnable r,String name){Thread t=new Thread(r,name);t.setDaemon(true);return t;}
    private static class Job {
        final String key,path; volatile MediaJobState state=MediaJobState.QUEUED; volatile String thumbnailState="queued",codec="",message="";
        volatile Double duration;volatile Integer width,height;volatile boolean direct;MediaProbe probe;PlaybackPlan plan;
        Job(String key,String path){this.key=key;this.path=path;}
        synchronized void move(MediaJobState next){state=state.transition(next);}
        Info info(String token,String selected){return new Info(key,state.wire(),selected==null?thumbnailState:"ready",duration,width,height,codec,direct?"original":"converted",message,token,selected==null?key:selected);}
    }
    public MediaJobs(Settings settings,MediaStore storage,UiLocale locale,GeneratedDataWorkspace workspace){this.settings=settings;this.storage=storage;this.locale=locale;this.budget=workspace;this.tools=new FfmpegAdapter(settings,workspace);this.tokens=new PlaybackTokens(settings,storage);this.images=new MediaImages(settings,storage);}
    public Info inspect(String input,boolean play,boolean retry) throws IOException {
        String path=storage.normalize(input),key=storage.version(path);Job job;
        synchronized(jobs){
            job=jobs.get(key);
            if(retry && job!=null && job.state==MediaJobState.FAILED){jobs.remove(key);job=null;}
            if(job==null){
                if(jobs.size()>=200){var it=jobs.entrySet().iterator();while(it.hasNext()){var old=it.next();if(old.getValue().state.evictable()){it.remove();break;}}}
                if(jobs.size()>=200)throw new RejectedExecutionException();
                job=new Job(key,path);jobs.put(key,job);Job submitted=job;
                try{analysis.execute(()->analyze(submitted));}catch(RejectedExecutionException e){jobs.remove(key);throw e;}
            }
        }
        synchronized(job){
            if(play && job.state==MediaJobState.INSPECTED) {
                if(Files.isRegularFile(playback(job))){job.move(MediaJobState.READY);}
                else {job.move(MediaJobState.PREPARING);Job submitted=job;try{conversion.execute(()->convert(submitted));}catch(RejectedExecutionException e){job.move(MediaJobState.INSPECTED);throw e;}}
            }
            if(job.state==MediaJobState.READY && !job.direct && !Files.isRegularFile(playback(job)))job.move(MediaJobState.INSPECTED);
            return job.info(job.state==MediaJobState.READY?playbackToken(path,key,job.direct):"",selectedThumbnailVersion(path));
        }
    }
    private void analyze(Job job){
        job.move(MediaJobState.ANALYZING);
        try{
            budget.enforceBudget();Path input=storage.processInput(job.path);
            job.probe=tools.probe(input);job.plan=planner.choose(job.path,job.probe);
            job.duration=job.probe.duration();job.width=job.probe.width();job.height=job.probe.height();job.codec=job.probe.codec();job.direct=job.plan.direct();
            job.move(job.direct || Files.isRegularFile(playback(job))?MediaJobState.READY:MediaJobState.INSPECTED);
            Path thumb=thumbnail(job);
            if(!Files.isRegularFile(thumb)){
                Path partial=settings.dataRoot().resolve("work").resolve(job.key+".part.jpg");
                try{
                    budget.enforceBudget();
                    tools.thumbnail(input,job.probe.videoIndex(),partial);
                    Files.move(partial,thumb,StandardCopyOption.REPLACE_EXISTING);
                }catch(Exception e){job.thumbnailState="unavailable";Files.deleteIfExists(partial);}
            }
            if(Files.isRegularFile(thumb))job.thumbnailState="ready";
        }catch(Exception e){job.move(MediaJobState.FAILED);job.thumbnailState="unavailable";job.message=locale.text("Cannot read the video information. Check the format, storage connection and FFmpeg installation.","영상 정보를 읽을 수 없습니다. 형식, 저장소 연결과 FFmpeg 설치를 확인해 주세요.");}
    }
    private void convert(Job job){
        Path partial=settings.dataRoot().resolve("work").resolve(job.key+".part.mp4");
        try{
            budget.enforceBudget();Path input=storage.processInput(job.path);
            tools.prepare(input,job.probe,job.plan,partial);
            if(!storage.version(job.path).equals(job.key))throw new IOException("Source changed during conversion");
            Files.move(partial,playback(job),StandardCopyOption.REPLACE_EXISTING);
            synchronized(job){if(job.state!=MediaJobState.FAILED)job.move(MediaJobState.READY);}
        }catch(Exception e){job.move(MediaJobState.FAILED);job.message=locale.text("Cannot prepare this video for playback. Check available storage and the original file.","재생용 영상을 준비하지 못했습니다. 저장 공간과 원본 상태를 확인해 주세요.");try{Files.deleteIfExists(partial);}catch(IOException ignored){}}
    }
    private Path thumbnail(Job job){return settings.dataRoot().resolve("thumbnails").resolve(job.key+".jpg");}
    private Path playback(Job job){return settings.dataRoot().resolve("playback").resolve(job.key+".mp4");}
    // Sorting needs duration only; do not generate every thumbnail before displaying a page.
    public Double probeDuration(String path) throws IOException {
        Future<Double> task = analysis.submit(() -> {
            try {
                return tools.duration(storage.processInput(path));
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
            catch (Exception e) { return null; }
        });
        try {
            return task.get(35, TimeUnit.SECONDS);
        } catch (InterruptedException e) { task.cancel(true); Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
        catch (TimeoutException e) { task.cancel(true); throw new RejectedExecutionException("Duration preparation is busy", e); }
        catch (ExecutionException e) { return null; }
    }
    public String selectedThumbnailVersion(String path){return images.selectedThumbnailVersion(path);}
    public Resource thumbnailResource(String path,String version)throws IOException{return images.thumbnailResource(path,version);}
    public String playbackToken(String path,String version,boolean original){return tokens.sign(path,version,original);}
    public Path prepared(String path,String version,String token)throws IOException{return tokens.prepared(path,version,token);}
    @PreDestroy public void close(){analysis.shutdownNow();conversion.shutdownNow();tools.close();}
}
