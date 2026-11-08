package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SeoTests {
    @Test void onlyTheGenericIntroductionIsIndexableAndConfiguredOriginIgnoresHost() throws Exception {
        var settings=new Settings(Path.of("movie"),Path.of("runtime"),120,48,8,120,4,10_737_418_240L,0,"ffmpeg","ffprobe");
        var mvc=MockMvcBuilders.standaloneSetup(new SeoController("https://media.example.org/",new PresentationService("child-toy","{}")))
            .addFilters(new WebConfiguration(settings).headers()).build();
        var response=mvc.perform(get("/about").header("Host","attacker.example")).andExpect(status().isOk())
            .andExpect(header().string("X-Robots-Tag","index, follow")).andExpect(content().contentType("text/html;charset=UTF-8")).andReturn().getResponse();
        String html=response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("rel=\"canonical\" href=\"https://media.example.org/about\""));assertTrue(html.contains("property=\"og:url\""));
        for(String privateValue:new String[]{"attacker.example","/api/","/js/app.js","?path=","VideoObject","{{"})assertFalse(html.contains(privateValue),privateValue);
        String json=html.split("<script type=\"application/ld\\+json\">",2)[1].split("</script>",2)[0];
        assertEquals("WebPage",new tools.jackson.databind.json.JsonMapper().readTree(json).path("@type").asText());
        String hash=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        assertTrue(response.getHeader("Content-Security-Policy").contains("'sha256-"+hash+"'"));
        mvc.perform(get("/about").header("If-None-Match",response.getHeader("ETag"))).andExpect(status().isNotModified());
        mvc.perform(get("/sitemap.xml").header("Host","attacker.example")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<loc>https://media.example.org/about</loc>")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("attacker.example"))));
        mvc.perform(get("/robots.txt")).andExpect(status().isOk()).andExpect(content().string("User-agent: *\nAllow: /\nSitemap: https://media.example.org/sitemap.xml\n"));
        mvc.perform(get("/catalog")).andExpect(header().string("X-Robots-Tag","noindex, nofollow, noimageindex"));
    }
    @Test void missingOriginOmitsCanonicalAndSitemapRatherThanTrustingIncomingHeaders() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new SeoController("",new PresentationService("child-toy","{}"))).build();
        mvc.perform(get("/about").header("Host","arbitrary.example")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("rel=\"canonical\""))));
        mvc.perform(get("/sitemap.xml")).andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void introductionUsesTheRegisteredRuntimeThemeWithoutCatalogJavascript()throws Exception {
        for(String id:new String[]{"child-toy","20s-midnight-stream"}) {
            var presentation=new PresentationService(id,"{}");String directory=presentation.theme().directory();
            var response=MockMvcBuilders.standaloneSetup(new SeoController("",presentation)).build().perform(get("/about"))
                .andExpect(status().isOk()).andReturn().getResponse();
            String html=response.getContentAsString(StandardCharsets.UTF_8);
            assertTrue(html.contains("data-theme=\""+id+"\""));assertTrue(html.contains("data-ready=\"true\""));
            for(String asset:new String[]{"css/app.css","themes/"+directory+"/theme.css","themes/"+directory+"/assets/hero.svg","css/about.css"}) {
                byte[] bytes;try(var input=new org.springframework.core.io.ClassPathResource("static/"+asset).getInputStream()){bytes=input.readAllBytes();}
                assertTrue(html.contains("/"+asset+"?v="+ByteCache.tag(bytes).substring(1,17)),asset);
            }
            assertTrue(html.contains("class=\"site-header\""));assertTrue(html.contains("class=\"brand-mark\""));assertTrue(html.contains("class=\"page-footer\""));
            assertFalse(html.contains("/api/"));assertFalse(html.contains("/js/"));assertFalse(html.contains("{{"));
        }
    }
    @Test void originSettingRejectsCredentialsPathsAndNonHttpSchemes() {
        for(String url:new String[]{"javascript:alert(1)","https://a.example/path","https://user:secret@a.example","https://a.example?name=x","https://a.example/#a","https://a.example:0","https://a.example:70000"})
            assertThrows(IllegalArgumentException.class,()->SeoController.validateOrigin(url));
        assertEquals("http://localhost:8080",SeoController.validateOrigin("http://localhost:8080/"));
    }
}
