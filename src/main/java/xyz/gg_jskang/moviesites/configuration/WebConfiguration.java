package xyz.gg_jskang.moviesites.configuration;

import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.presentation.PresentationService;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.configuration.internal.StaticAssets;

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
    private final StaticAssets assets;
    private final UiLocale locale;
    private final Settings settings;
    public WebConfiguration(Settings settings){this(settings,new UiLocale("en"));}
    public WebConfiguration(Settings settings,UiLocale locale){this(settings,locale,"#101015");}
    @org.springframework.beans.factory.annotation.Autowired
    public WebConfiguration(Settings settings,UiLocale locale,PresentationService presentation){this(settings,locale,presentation.themeColor());}
    private WebConfiguration(Settings settings,UiLocale locale,String themeColor){this.settings=settings;this.locale=locale;this.assets=new StaticAssets(locale,themeColor);}
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
            res.setHeader("Content-Language",locale.id());
            res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Referrer-Policy","no-referrer");
            res.setHeader("X-Robots-Tag","noindex, nofollow, noimageindex");
            res.setHeader("Content-Security-Policy","default-src 'self'; img-src 'self' data:; media-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
            if(req.getRequestURI().startsWith("/api/")&&!req.getRequestURI().equals("/api/media/thumbnail"))res.setHeader("Cache-Control","no-store");
            if(assets.serve(req,res))return;
            boolean fileRequest=req.getRequestURI().startsWith("/api/media/");
            if(fileRequest&&!fileRequests.tryAcquire()){res.setStatus(429);res.setHeader("Cache-Control","no-store");res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"code\":\"BUSY\",\"message\":\""+locale.text("Too many requests. Try again in a moment.","처리 중인 요청이 많습니다. 잠시 후 다시 시도해 주세요.")+"\"}");return;}
            try{chain.doFilter(req,res);}finally{if(fileRequest)fileRequests.release();}
        }
    };}
}
