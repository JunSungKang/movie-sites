package xyz.gg_jskang.moviesites.media.internal;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;
import java.util.concurrent.*;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.storage.GeneratedDataWorkspace;
/** The only child-process boundary, with shared process, time, output and storage limits. */
final class FfmpegAdapter implements MediaTools {
    private static final String FORMATS="mov,matroska,webm,avi,mpeg,mpegts,ogg,asf,flv";
    private final JsonMapper json=new JsonMapper();
    private final Settings settings;
    private final GeneratedDataWorkspace workspace;
    private final Set<Process> processes=ConcurrentHashMap.newKeySet();
    FfmpegAdapter(Settings settings,GeneratedDataWorkspace workspace){this.settings=settings;this.workspace=workspace;}
    @Override public MediaProbe probe(Path input)throws Exception {
        String result=run(List.of(settings.ffprobe(),"-v","error","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-probesize","5000000","-analyzeduration","5000000","-show_entries","format=duration:stream=index,codec_type,codec_name,width,height,pix_fmt,color_transfer:stream_disposition=attached_pic","-of","json",input.toString()),null,30);
        JsonNode root=json.readTree(result),video=null;
        int videoIndex=0,audioIndex=-1,videoCount=0,audioCount=0;String audio="";
        for(JsonNode stream:root.path("streams")) {
            if(stream.path("codec_type").asText().equals("video")){videoCount++;if(video==null&&stream.path("disposition").path("attached_pic").asInt(0)==0){video=stream;videoIndex=stream.path("index").asInt();}}
            else if(stream.path("codec_type").asText().equals("audio")){audioCount++;if(audioIndex<0){audioIndex=stream.path("index").asInt();audio=stream.path("codec_name").asText();}}
        }
        if(video==null)throw new IOException("No video stream");
        Double duration=null;try{double d=Double.parseDouble(root.path("format").path("duration").asText());if(Double.isFinite(d)&&d>=0)duration=d;}catch(NumberFormatException ignored){}
        return new MediaProbe(duration,video.path("width").asInt(),video.path("height").asInt(),video.path("codec_name").asText(),audio,video.path("pix_fmt").asText(),video.path("color_transfer").asText(),videoIndex,audioIndex,videoCount,audioCount);
    }
    @Override public Double duration(Path input)throws Exception {
        String result=run(List.of(settings.ffprobe(),"-v","error","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-probesize","5000000","-analyzeduration","5000000","-show_entries","format=duration","-of","json",input.toString()),null,30);
        double value=Double.parseDouble(json.readTree(result).path("format").path("duration").asText());
        return Double.isFinite(value)&&value>0?value:null;
    }
    @Override public void thumbnail(Path input,int videoIndex,Path output)throws Exception {
        run(List.of(settings.ffmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-i",input.toString(),"-map","0:"+videoIndex,"-frames:v","1","-vf","scale=640:-2","-threads","1","-q:v","3",output.toString()),output,45);
    }
    @Override public void prepare(Path input,MediaProbe probe,PlaybackPlan plan,Path output)throws Exception {
        if(plan.direct())throw new IllegalArgumentException("Direct playback does not need a generated file");
        var command=new ArrayList<>(List.of(settings.ffmpeg(),"-nostdin","-hide_banner","-loglevel","error","-y","-protocol_whitelist","file,pipe","-format_whitelist",FORMATS,"-i",input.toString(),"-map","0:"+probe.videoIndex(),"-map_metadata","-1","-map_chapters","-1"));
        if(probe.audioIndex()>=0)command.addAll(List.of("-map","0:"+probe.audioIndex()));
        command.addAll(plan.arguments(probe));command.addAll(List.of("-movflags","+faststart",output.toString()));
        run(command,output,21600);
    }
    private String run(List<String> command,Path output,long timeoutSeconds) throws Exception {
        Path log=Files.createTempFile(settings.dataRoot().resolve("work"),"process-",".log");
        Process process=null;
        try{
            synchronized(processes){processes.removeIf(p->!p.isAlive());if(processes.size()>=3)throw new RejectedExecutionException();process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(process);}
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeoutSeconds);long lastBudgetCheck=0;
            while(!process.waitFor(250,TimeUnit.MILLISECONDS)){
                if(output!=null&&(System.nanoTime()-lastBudgetCheck>TimeUnit.SECONDS.toNanos(1)||Files.getFileStore(settings.dataRoot()).getUsableSpace()<settings.minFreeBytes())){lastBudgetCheck=System.nanoTime();workspace.enforceBudget();}
                if(System.nanoTime()>deadline || Files.size(log)>1_048_576 || (output!=null && (Files.exists(output) && Files.size(output)>settings.maxCacheBytes())))throw new IOException("Processing resource limit reached");
            }
            if(process.exitValue()!=0)throw new IOException("Media process failed");
            if(output!=null){if(Files.size(output)>settings.maxCacheBytes())throw new IOException("Output exceeds cache budget");workspace.enforceBudget();}
            if(Files.size(log)>1_048_576)throw new IOException("Process output limit reached");
            return Files.readString(log);
        }finally{
            if(process!=null){if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}if(!process.isAlive())processes.remove(process);}
            Files.deleteIfExists(log);
        }
    }
    @Override public void close(){for(Process process:processes)process.destroyForcibly();}
}
