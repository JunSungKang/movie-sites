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

class StaticAssetsTests {
    @Configuration @EnableWebMvc @Import(WebConfiguration.class)
    static class TestConfig {
        @Bean Settings settings(){return new Settings(Path.of("movie"),Path.of("runtime"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");}
    }
    @Test void cachedBuildTimestampCannotSuppressUpdatedStaticAssets()throws Exception{
        try(var context=new AnnotationConfigWebApplicationContext()){
            context.setServletContext(new MockServletContext());context.register(TestConfig.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).build();
            for(String path:new String[]{"/index.html","/js/app.js","/js/cache.js","/css/app.css"}){
                mvc.perform(get(path).header(HttpHeaders.IF_MODIFIED_SINCE,"Sun, 07 Nov 2027 00:00:00 GMT"))
                        .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL,"no-store"))
                        .andExpect(header().doesNotExist(HttpHeaders.LAST_MODIFIED));
            }
            mvc.perform(get("/js/app.js")).andExpect(content().string(org.hamcrest.Matchers.containsString("streamToken")));
        }
    }
}
