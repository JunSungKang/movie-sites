package io.moviesites;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.filter.OncePerRequestFilter;

class StaticAssetsTests {
    @Configuration @EnableWebMvc @Import(WebConfiguration.class)
    static class TestConfig {
        @Bean UiLocale uiLocale(@org.springframework.beans.factory.annotation.Value("${moviesites.ui-locale:en}") String selected){return new UiLocale(selected);}
        @Bean Settings settings(){return new Settings(Path.of("movie"),Path.of("runtime"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");}
    }
    @Test void cachedBuildTimestampCannotSuppressUpdatedStaticAssets()throws Exception{
        try(var context=new AnnotationConfigWebApplicationContext()){
            context.setServletContext(new MockServletContext());context.register(TestConfig.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(OncePerRequestFilter.class)).build();
            for(String path:new String[]{"/index.html","/js/app.js","/js/cache.js","/css/app.css","/themes/catalog.json","/themes/runtime.js","/themes/10s/neon-lab/theme.css","/themes/60s/bold-contrast/assets/preview.svg"}){
                mvc.perform(get(path).header(HttpHeaders.IF_MODIFIED_SINCE,"Sun, 07 Nov 2027 00:00:00 GMT"))
                        .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL,path.equals("/index.html")?"no-store":"public, no-cache"))
                        .andExpect(header().doesNotExist(HttpHeaders.LAST_MODIFIED));
            }
            mvc.perform(get("/js/app.js")).andExpect(content().string(org.hamcrest.Matchers.containsString("streamToken")));
            byte[] bytes=new org.springframework.core.io.ClassPathResource("static/js/app.js").getInputStream().readAllBytes();
            String etag="W/"+ByteCache.tag(bytes),hash=ByteCache.tag(bytes).substring(1,17);
            mvc.perform(get("/js/app.js").param("v",hash)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control","public, max-age=31536000, immutable")).andExpect(header().string("ETag",etag));
            mvc.perform(get("/js/app.js").param("v","old-release")).andExpect(header().string("Cache-Control","public, no-cache"));
            mvc.perform(get("/js/app.js").header("If-None-Match",etag)).andExpect(status().isNotModified()).andExpect(content().string(""));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/js/app.js")).andExpect(status().isOk()).andExpect(content().string(""));
        }
    }
}
