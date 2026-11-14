package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

class PwaTests {
    @Test void manifestIconsAndWorkerAreServedWithSafeTypesCachingAndSearchExclusion()throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.register(StaticAssetsTests.TestConfig.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(OncePerRequestFilter.class)).build();
            var response=mvc.perform(get("/manifest.webmanifest")).andExpect(status().isOk())
                .andExpect(content().contentType("application/manifest+json;charset=UTF-8"))
                .andExpect(header().string("Cache-Control","public, no-cache"))
                .andExpect(header().string("X-Robots-Tag","noindex, nofollow, noimageindex")).andReturn().getResponse();
            var manifest=new JsonMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
            assertEquals("/",manifest.path("id").asText());assertEquals("/",manifest.path("start_url").asText());
            assertEquals("en",manifest.path("lang").asText());
            assertEquals("/",manifest.path("scope").asText());assertEquals("standalone",manifest.path("display").asText());
            for(var icon:manifest.path("icons")) {
                String path=icon.path("src").asText();
                mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
                try(var input=new ClassPathResource("static"+path).getInputStream()) {
                    var image=ImageIO.read(input);assertNotNull(image);
                    assertEquals(icon.path("sizes").asText(),image.getWidth()+"x"+image.getHeight());
                    if(icon.path("purpose").asText().equals("maskable")) {
                        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)assertEquals(255,(image.getRGB(x,y)>>>24)&255);
                    }
                }
            }
            byte[] worker;try(var input=new ClassPathResource("static/service-worker.js").getInputStream()){worker=input.readAllBytes();}
            String hash=ByteCache.tag(worker).substring(1,17);
            mvc.perform(get("/service-worker.js").param("v",hash)).andExpect(status().isOk())
                .andExpect(content().contentType("text/javascript;charset=UTF-8"))
                .andExpect(header().string("Cache-Control","public, no-cache"))
                .andExpect(header().string("Service-Worker-Allowed","/"))
                .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("script-src 'self'")));
            mvc.perform(head("/service-worker.js")).andExpect(status().isOk()).andExpect(content().string(""));
            mvc.perform(get("/service-worker.js").header("If-None-Match","W/"+ByteCache.tag(worker))).andExpect(status().isNotModified());
            mvc.perform(get("/offline.html")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/api/"))));
        }
    }
    @Test void startupLocaleSelectsHtmlAndManifestWithoutAcceptLanguageNegotiationOrStaleEtags()throws Exception {
        String englishTag = null;
        for(String locale:new String[]{"en","ko"}) {
            try(var context=new AnnotationConfigWebApplicationContext()) {
                context.setServletContext(new MockServletContext());
                context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("locale-test",java.util.Map.of("moviesites.ui-locale",locale)));
                context.register(StaticAssetsTests.TestConfig.class);context.refresh();
                var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(OncePerRequestFilter.class)).build();
                for(String path:new String[]{"/","/index.html","/themes/preview.html"}) {
                    var page=mvc.perform(get(path).header("Accept-Language",locale.equals("en")?"ko":"en")
                            .header("If-Modified-Since","Sun, 07 Nov 2027 00:00:00 GMT"))
                        .andExpect(status().isOk()).andExpect(header().string("Content-Language",locale))
                        .andExpect(header().string("Cache-Control","no-store"))
                        .andExpect(content().contentType("text/html;charset=UTF-8")).andReturn().getResponse();
                    assertTrue(page.getContentAsString(StandardCharsets.UTF_8).contains("<html lang=\""+locale+"\""));
                    mvc.perform(head(path)).andExpect(status().isOk()).andExpect(content().string(""));
                }
                var request=get("/manifest.webmanifest").param("v","a-client-must-revalidate-this-resource");
                if(englishTag!=null)request.header("If-None-Match",englishTag);
                var response=mvc.perform(request).andExpect(status().isOk())
                    .andExpect(header().string("Content-Language",locale))
                    .andExpect(header().string("Cache-Control","public, no-cache")).andReturn().getResponse();
                var manifest=new JsonMapper().readTree(response.getContentAsString(StandardCharsets.UTF_8));
                assertEquals(locale,manifest.path("lang").asText());
                String selectedTag=response.getHeader("ETag");
                mvc.perform(get("/manifest.webmanifest").header("If-None-Match",selectedTag))
                    .andExpect(status().isNotModified()).andExpect(content().string(""));
                if(locale.equals("en"))englishTag=selectedTag;
                else assertNotEquals(englishTag,selectedTag);
            }
        }
    }

}
