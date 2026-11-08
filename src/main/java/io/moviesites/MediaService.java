package io.moviesites;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MediaService {
    public record Info(String version,String state,String thumbnailState,Double duration,Integer width,Integer height,String codec,String playback,String message,String streamToken,String thumbnailVersion) {}
    private static final String FORMATS="mov,matroska,webm,avi,mpeg,mpegts,ogg,asf,flv";
    private final Settings settings; private final Storage storage;
    private final SecretKey playbackKey=createPlaybackKey();
    private final JsonMapper json=new JsonMapper();
    private final Map<String,Job> jobs=new LinkedHashMap<>(32,.75f,true);
    private final Set<Process> processes=ConcurrentHashMap.newKeySet();
    private final CacheBudget budget;
    private final ThreadPoolExecutor analysis=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->daemon(r,"media-analysis"));
    private final ThreadPoolExecutor conversion=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->daemon(r,"media-conversion"));
    private final AtomicInteger streams=new AtomicInteger();
    private static Thread daemon(Runnable r,String name){Thread t=new Thread(r,name);t.setDaemon(true);return t;}
    private static class Job {
        final String key,path; volatile String state="queued",thumbnailState="queued",codec="",audio="",pixel="",transfer="",message="";
        volatile Double duration;volatile Integer width,height;volatile boolean direct,copyCompatible;int videoIndex,audioIndex=-1,videoCount,audioCount;
        Job(String key,String path){this.key=key;this.path=path;}
        Info info(String token,String selected){return new Info(key,state,selected==null?thumbnailState:"ready",duration,width,height,codec,direct?"original":"converted",message,token,selected==null?key:selected);}
    }
    public MediaService(Settings settings,Storage storage)throws IOException{this.settings=settings;this.storage=storage;this.budget=new CacheBudget(settings);}
    public Info inspect(String input,boolean play,boolean retry) throws IOException {
        String path=storage.normalize(input),key=storage.version(path);Job job;
        synchronized(jobs){
            job=jobs.get(key);
            if(retry && job!=null && job.state.equals("failed")){jobs.remove(key);job=null;}
            if(job==null){
                if(jobs.size()>=200){var it=jobs.entrySet().iterator();while(it.hasNext()){var old=it.next();if(Set.of("ready","failed","inspected").contains(old.getValue().state)){it.remove();break;}}}
                if(jobs.size()>=200)throw new RejectedExecutionException();
                job=new Job(key,path);jobs.put(key,job);Job submitted=job;
                try{analysis.execute(()->analyze(submitted));}catch(RejectedExecutionException e){jobs.remove(key);throw e;}
            }
        }
        synchronized(job){
            if(play && job.state.equals("inspected")) {
                if(Files.isRegularFile(playback(job))){job.state="ready";}
                else {job.state="preparing";Job submitted=job;try{conversion.execute(()->convert(submitted));}catch(RejectedExecutionException e){job.state="inspected";throw e;}}
            }
            if(job.state.equals("ready") && !job.direct && !Files.isRegularFile(playback(job)))job.state="inspected";
            return job.info(job.state.equals("ready")?playbackToken(path,key,job.direct):"",selectedThumbnailVersion(path));
        }
    }
    private void analyze(Job job){
        job.state="analyzing";
        try{
            budget.enforce();Path input=storage.processInput(job.path);
            String result=run(List.of(settings.ffprobe(),"-v","error","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-probesize","5000000","-analyzeduration","5000000","-show_entries","format=duration:stream=index,codec_type,codec_name,width,height,pix_fmt,color_transfer:stream_disposition=attached_pic","-of","json",input.toString()),null,30);
            JsonNode root=json.readTree(result);JsonNode video=null;
            for(JsonNode stream:root.path("streams")){
                if(stream.path("codec_type").asText().equals("video")){job.videoCount++;if(video==null&&stream.path("disposition").path("attached_pic").asInt(0)==0){video=stream;job.videoIndex=stream.path("index").asInt();}}
                else if(stream.path("codec_type").asText().equals("audio")){job.audioCount++;if(job.audioIndex<0){job.audioIndex=stream.path("index").asInt();job.audio=stream.path("codec_name").asText();}}
            }
            if(video==null)throw new IOException("No video stream");
            job.codec=video.path("codec_name").asText();job.pixel=video.path("pix_fmt").asText();job.transfer=video.path("color_transfer").asText();
            job.width=video.path("width").asInt();job.height=video.path("height").asInt();
            try{double d=Double.parseDouble(root.path("format").path("duration").asText());if(Double.isFinite(d)&&d>=0)job.duration=d;}catch(NumberFormatException ignored){}
            job.copyCompatible=job.codec.equals("h264") && job.pixel.equals("yuv420p") && (job.audioIndex<0||job.audio.equals("aac"));
            job.direct=job.copyCompatible && job.videoCount==1 && job.audioCount<=1 && job.path.toLowerCase(Locale.ROOT).endsWith(".mp4");
            job.state=job.direct || Files.isRegularFile(playback(job))?"ready":"inspected";
            Path thumb=thumbnail(job);
            if(!Files.isRegularFile(thumb)){
                Path partial=settings.dataRoot().resolve("work").resolve(job.key+".part.jpg");
                try{
                    budget.enforce();
                    run(List.of(settings.ffmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-i",input.toString(),"-map","0:"+job.videoIndex,"-frames:v","1","-vf","scale=640:-2","-threads","1","-q:v","3",partial.toString()),partial,45);
                    Files.move(partial,thumb,StandardCopyOption.REPLACE_EXISTING);
                }catch(Exception e){job.thumbnailState="unavailable";Files.deleteIfExists(partial);}
            }
            if(Files.isRegularFile(thumb))job.thumbnailState="ready";
        }catch(Exception e){job.state="failed";job.thumbnailState="unavailable";job.message="영상 정보를 읽을 수 없습니다. 형식, 저장소 연결과 FFmpeg 설치를 확인해 주세요.";}
    }
    private void convert(Job job){
        Path partial=settings.dataRoot().resolve("work").resolve(job.key+".part.mp4");
        try{
            budget.enforce();Path input=storage.processInput(job.path);
            List<String> command=new ArrayList<>(List.of(settings.ffmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-i",input.toString(),"-map","0:"+job.videoIndex,"-map_metadata","-1","-map_chapters","-1"));
            if(job.audioIndex>=0)command.addAll(List.of("-map","0:"+job.audioIndex));
            if(job.copyCompatible)command.addAll(List.of("-c","copy"));
            else{
                String filter="scale=w='min(1920,iw)':h='min(1080,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,format=yuv420p";
                if(Set.of("smpte2084","arib-std-b67").contains(job.transfer))filter="zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=hable,zscale=t=bt709:m=bt709:r=tv,"+filter;
                command.addAll(List.of("-vf",filter,"-c:v","libx264","-preset","veryfast","-crf","22","-threads","2","-c:a","aac","-b:a","160k","-ac","2"));
            }
            command.addAll(List.of("-movflags","+faststart",partial.toString()));
            run(command,partial,21600);
            if(!storage.version(job.path).equals(job.key))throw new IOException("Source changed during conversion");
            Files.move(partial,playback(job),StandardCopyOption.REPLACE_EXISTING);
            job.state="ready";
        }catch(Exception e){job.state="failed";job.message="재생용 영상을 준비하지 못했습니다. 저장 공간과 원본 상태를 확인해 주세요.";try{Files.deleteIfExists(partial);}catch(IOException ignored){}}
    }
    private String run(List<String> command,Path output,long timeoutSeconds) throws Exception {
        Path log=Files.createTempFile(settings.dataRoot().resolve("work"),"process-",".log");
        Process process=null;
        try{
            synchronized(processes){processes.removeIf(p->!p.isAlive());if(processes.size()>=3)throw new RejectedExecutionException();process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(process);}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeoutSeconds);long lastBudgetCheck=0;
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)){
                if(output!=null&&(System.nanoTime()-lastBudgetCheck>TimeUnit.SECONDS.toNanos(1)||Files.getFileStore(settings.dataRoot()).getUsableSpace()<settings.minFreeBytes())){lastBudgetCheck=System.nanoTime();budget.enforce();}
                if(System.nanoTime()>deadline || Files.size(log)>1_048_576 || (output!=null && (Files.exists(output) && Files.size(output)>settings.maxCacheBytes())))throw new IOException("Processing resource limit reached");
            }
            if(process.exitValue()!=0)throw new IOException("Media process failed");
            if(output!=null){if(Files.size(output)>settings.maxCacheBytes())throw new IOException("Output exceeds cache budget");budget.enforce();}
            if(Files.size(log)>1_048_576)throw new IOException("Process output limit reached");
            return Files.readString(log);
        }finally{
            if(process!=null){if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}if(!process.isAlive())processes.remove(process);}
            Files.deleteIfExists(log);
        }
    }
    private Path thumbnail(Job job){return settings.dataRoot().resolve("thumbnails").resolve(job.key+".jpg");}
    private Path playback(Job job){return settings.dataRoot().resolve("playback").resolve(job.key+".mp4");}
    // Sorting needs duration only; do not generate every thumbnail before displaying a page.
    Double probeDuration(String path) throws IOException {
        Future<Double> task = analysis.submit(() -> {
            try {
                String result = run(List.of(settings.ffprobe(), "-v", "error", "-protocol_whitelist", "file,pipe", "-format_whitelist", FORMATS,
                    "-probesize", "5000000", "-analyzeduration", "5000000", "-show_entries", "format=duration", "-of", "json", storage.processInput(path).toString()), null, 30);
                double value = Double.parseDouble(json.readTree(result).path("format").path("duration").asText());
                return Double.isFinite(value) && value > 0 ? value : null;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
            catch (Exception e) { return null; }
        });
        try {
            return task.get(35, TimeUnit.SECONDS);
        } catch (InterruptedException e) { task.cancel(true); Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
        catch (TimeoutException e) { task.cancel(true); throw new RejectedExecutionException("Duration preparation is busy", e); }
        catch (ExecutionException e) { return null; }
    }
    void enforceCacheBudget() throws IOException { budget.enforce(); }
    // An explicit sidecar travels with its video and is never evicted as generated cache.
    // Its own version must not change the video's stream token or playback cache key.
    String selectedThumbnailVersion(String path) {
        try { new FolderCoverService(storage).readImage(storage.normalize(path)+".jpg");return storage.version(path+".jpg"); }
        catch(IOException|IllegalArgumentException e){return null;}
    }
    public Resource thumbnailResource(String path,String version) throws IOException {
        String videoVersion=storage.version(path);
        if(version.equals(selectedThumbnailVersion(path)))return new ByteArrayResource(new FolderCoverService(storage).readImage(path+".jpg"));
        if(!videoVersion.equals(version))throw new NoSuchFileException("Thumbnail version changed");
        Path p=settings.dataRoot().resolve("thumbnails").resolve(version+".jpg");
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException("Thumbnail is not ready");return new FileSystemResource(p);
    }
    private static SecretKey createPlaybackKey(){try{return KeyGenerator.getInstance("HmacSHA256").generateKey();}catch(GeneralSecurityException e){throw new IllegalStateException(e);}}
    String playbackToken(String path,String version,boolean original){
        String mode=original?"original":"converted";
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(playbackKey);return mode+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((storage.normalize(path)+"\0"+version+"\0"+mode).getBytes(StandardCharsets.UTF_8)));}
        catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    public Path prepared(String path,String version,String token) throws IOException {
        if(!storage.version(path).equals(version))throw new IllegalStateException("Source changed");
        boolean original=token!=null&&token.startsWith("original.");
        String expected=playbackToken(path,version,original);
        if(token==null||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),token.getBytes(StandardCharsets.US_ASCII)))throw new AccessDeniedException("Invalid playback authorization");
        if(original)return null;
        Path file=settings.dataRoot().resolve("playback").resolve(version+".mp4");
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Playback cache expired");
        return file;
    }
    public boolean acquireStream(){int value=streams.incrementAndGet();if(value>settings.maxStreams()){streams.decrementAndGet();return false;}return true;}
    public void releaseStream(){streams.decrementAndGet();}
    public void pin(Path path){budget.pin(path);}
    public void unpin(Path path){budget.unpin(path);}
    @PreDestroy public void close(){analysis.shutdownNow();conversion.shutdownNow();for(Process p:processes)p.destroyForcibly();}
}
