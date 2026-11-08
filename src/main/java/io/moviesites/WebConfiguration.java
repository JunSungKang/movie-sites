package io.moviesites;

import java.util.Map;
import java.util.concurrent.*;
import java.io.IOException;
import java.nio.file.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final StaticAssets assets=new StaticAssets();
    private final Settings settings;
    public WebConfiguration(Settings settings){this.settings=settings;}
    @Bean public ThreadPoolTaskExecutor streamingExecutor(){ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor();e.setCorePoolSize(settings.maxStreams());e.setMaxPoolSize(settings.maxStreams());e.setQueueCapacity(settings.maxStreams());e.setThreadNamePrefix("video-stream-");e.setAwaitTerminationSeconds(5);return e;}
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry){
        // Reproducible JAR timestamps do not identify changed content across releases.
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore()).setUseLastModified(false);
    }
    @Override public void configureAsyncSupport(AsyncSupportConfigurer configurer){configurer.setTaskExecutor(streamingExecutor());}
    @Bean public OncePerRequestFilter headers(){return new OncePerRequestFilter(){
        private final Semaphore fileRequests=new Semaphore(8);
        @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
            res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Referrer-Policy","no-referrer");
            res.setHeader("X-Robots-Tag","noindex, nofollow, noimageindex");
            res.setHeader("Content-Security-Policy","default-src 'self'; img-src 'self' data:; media-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
            if(req.getRequestURI().startsWith("/api/")&&!req.getRequestURI().equals("/api/media/thumbnail"))res.setHeader("Cache-Control","no-store");
            if(assets.serve(req,res))return;
            boolean fileRequest=req.getRequestURI().startsWith("/api/media/");
            if(fileRequest&&!fileRequests.tryAcquire()){res.setStatus(429);res.setHeader("Cache-Control","no-store");res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":\"BUSY\",\"message\":\"처리 중인 요청이 많습니다. 잠시 후 다시 시도해 주세요.\"}");return;}
            try{chain.doFilter(req,res);}finally{if(fileRequest)fileRequests.release();}
        }
    };}
    @RestControllerAdvice public static class Errors {
        @ExceptionHandler(Exception.class) public ResponseEntity<Map<String,String>> error(Exception exception){
            Throwable e=exception;while((e instanceof CompletionException||e instanceof ExecutionException)&&e.getCause()!=null)e=e.getCause();
            int status;String code,message;
            if(e instanceof IllegalArgumentException){status=400;code="INVALID_PATH";message="요청 경로 또는 설정을 확인해 주세요.";}
            else if(e instanceof DirectoryBrowser.ExpiredCursor){status=410;code="CURSOR_EXPIRED";message="목록 조회 시간이 만료되었습니다. 새로고침해 주세요.";}
            else if(e instanceof NoSuchFileException){status=404;code="NOT_FOUND";message="항목이 이동했거나 삭제되었습니다. 목록을 새로고침해 주세요.";}
            else if(e instanceof AccessDeniedException){status=403;code="ACCESS_DENIED";message="이 항목에 접근할 권한이 없습니다.";}
            else if(e instanceof RejectedExecutionException){status=429;code="BUSY";message="처리 중인 요청이 많습니다. 잠시 후 다시 시도해 주세요.";}
            else if(e instanceof IllegalStateException){status=409;code="NOT_READY";message="영상이 변경되었거나 재생 준비 중입니다. 다시 확인해 주세요.";}
            else if(e instanceof IOException||e instanceof TimeoutException){status=503;code="STORAGE_UNAVAILABLE";message="저장소에 연결할 수 없거나 응답이 지연되고 있습니다.";}
            else if(e instanceof org.springframework.web.ErrorResponse response){status=response.getStatusCode().value();code="HTTP_ERROR";message=status==404?"요청한 항목을 찾을 수 없습니다.":"요청 형식을 확인해 주세요.";}
            else{status=500;code="INTERNAL_ERROR";message="요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";}
            return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code",code,"message",message));
        }
    }
}
