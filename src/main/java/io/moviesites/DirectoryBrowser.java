package io.moviesites;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class DirectoryBrowser {
    public record Entry(String name,String path,String kind,long size,String modified){}
    public record Listing(String path,String parent,List<Entry> entries,String nextCursor,int warnings){}
    private static final Set<String> EXTENSIONS=Set.of("mp4","m4v","mov","mkv","webm","avi","mpeg","mpg","mts","m2ts","ts","ogv","wmv","flv","3gp","3g2","mxf","vob","av1");
    private final Settings settings;private final Storage storage;
    private final Map<String,Session> sessions=new LinkedHashMap<>();
    private final ThreadPoolExecutor readers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"directory-reader");t.setDaemon(true);return t;});
    private static class Session implements AutoCloseable {
        final String path;final SecureDirectoryStream<Path> stream;final Iterator<Path> iterator;final ReentrantLock lock=new ReentrantLock();
        volatile long touched=System.nanoTime();int step;
        Session(String path,SecureDirectoryStream<Path> stream){this.path=path;this.stream=stream;this.iterator=stream.iterator();}
        public void close()throws IOException{stream.close();}
    }
    public DirectoryBrowser(Settings settings,Storage storage){this.settings=settings;this.storage=storage;}
    public CompletableFuture<Listing> browseAsync(String path,String cursor){
        try{return CompletableFuture.supplyAsync(()->{try{return browse(path,cursor);}catch(IOException e){throw new CompletionException(e);}},readers).orTimeout(8,TimeUnit.SECONDS);}
        catch(RejectedExecutionException e){return CompletableFuture.failedFuture(e);}
    }
    private void retireIdle(boolean needRoom){
        List<Session> retired=new ArrayList<>();
        synchronized(sessions){
            var it=sessions.entrySet().iterator();
            while(it.hasNext()){
                var s=it.next().getValue();
                if((System.nanoTime()-s.touched>TimeUnit.SECONDS.toNanos(settings.browseIdleSeconds()) || needRoom&&sessions.size()>=settings.maxBrowseSessions())&&s.lock.tryLock()){
                    retired.add(s);it.remove();
                }
            }
        }
        for(var s:retired)try{s.close();}catch(IOException ignored){}finally{s.lock.unlock();}
    }
    public Listing browse(String input,String cursor)throws IOException{
        String path=storage.normalize(input);retireIdle(cursor==null||cursor.isBlank());
        Session session;String id;int step=0;
        if(cursor==null||cursor.isBlank()){
            session=new Session(path,storage.directory(path));id=UUID.randomUUID().toString();
            synchronized(sessions){if(sessions.size()>=settings.maxBrowseSessions()){session.close();throw new RejectedExecutionException();}sessions.put(id,session);}
        }else{
            String[] parts=cursor.split("\\.",-1);if(parts.length!=2)throw new ExpiredCursor();id=parts[0];
            try{step=Integer.parseInt(parts[1]);}catch(NumberFormatException e){throw new ExpiredCursor();}
            synchronized(sessions){session=sessions.get(id);}
            if(session==null||!session.path.equals(path))throw new ExpiredCursor();
        }
        session.lock.lock();
        try{
            synchronized(sessions){if(sessions.get(id)!=session)throw new ExpiredCursor();}
            if(step!=session.step)throw new ExpiredCursor();
            session.touched=System.nanoTime();List<Entry> entries=new ArrayList<>();int warnings=0,examined=0;
            while(entries.size()<settings.pageSize()&&examined++<1000&&session.iterator.hasNext()){
                Path file=session.iterator.next().getFileName();String name=file.toString();
                if(name.equals(".gitkeep")||name.equals(".DS_Store")||name.equals(".git"))continue;
                try{
                    var a=session.stream.getFileAttributeView(file,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
                    if(a.isSymbolicLink()){warnings++;continue;}
                    int dot=name.lastIndexOf('.');boolean video=dot>=0&&EXTENSIONS.contains(name.substring(dot+1).toLowerCase(Locale.ROOT));
                    if(a.isDirectory()||(a.isRegularFile()&&video))entries.add(new Entry(name,path.isEmpty()?name:path+"/"+name,a.isDirectory()?"folder":"video",a.isRegularFile()?a.size():0,a.lastModifiedTime().toInstant().toString()));
                }catch(IOException e){warnings++;}
            }
            boolean more=session.iterator.hasNext();session.step++;
            if(!more){synchronized(sessions){sessions.remove(id);}session.close();}
            String parent=path.isEmpty()?null:(Path.of(path).getParent()==null?"":Path.of(path).getParent().toString().replace('\\','/'));
            return new Listing(path,parent,List.copyOf(entries),more?id+"."+session.step:null,warnings);
        }catch(IOException|RuntimeException e){synchronized(sessions){sessions.remove(id);}try{session.close();}catch(IOException ignored){}throw e;}
        finally{session.lock.unlock();}
    }
    public static class ExpiredCursor extends RuntimeException{}
    @PreDestroy public void close(){readers.shutdownNow();synchronized(sessions){for(var s:sessions.values())try{s.close();}catch(IOException ignored){}sessions.clear();}}
}
