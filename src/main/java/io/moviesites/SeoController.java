package io.moviesites;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** The public introduction never reads or renders the external media catalog. */
@RestController
public class SeoController {
    private final String origin,html,csp;
    private final UiLocale locale;

    public SeoController(@Value("${moviesites.public-url:}")String url,PresentationService presentation) throws Exception {
        origin=validateOrigin(url);
        locale=presentation.uiLocale();
        String description=locale.text("Browse your video folders and choose by length or topic with MovieSites, an open-source video library.","내 영상 폴더를 그대로 탐색하고, 길이와 주제로 골라 보는 오픈소스 영상 라이브러리, MovieSites.");
        String title=locale.text("MovieSites — Your video library","MovieSites — 나의 영상 라이브러리");
        var theme=presentation.theme();
        tools.jackson.databind.JsonNode manifest;
        try(var input=new ClassPathResource("static/themes/"+theme.directory()+"/theme.json").getInputStream()) {
            manifest=new JsonMapper().readTree(input);
        }
        String color=manifest.path("color").asText();
        if(!color.matches("#[a-fA-F0-9]{6}"))throw new IllegalArgumentException("Invalid packaged theme color");
        Map<String,Object> data=new LinkedHashMap<>();data.put("@context","https://schema.org");data.put("@type","WebPage");data.put("name",title);data.put("description",description);data.put("inLanguage",locale.id());
        if(!origin.isEmpty())data.put("url",origin+"/about");
        String json=new JsonMapper().writeValueAsString(data).replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026");
        String hash=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        csp="default-src 'self'; img-src 'self' data:; media-src 'self'; script-src 'self' 'sha256-"+hash+"'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
        String canonical=origin.isEmpty()?"":"<link rel=\"canonical\" href=\""+escape(origin+"/about")+"\"><meta property=\"og:url\" content=\""+escape(origin+"/about")+"\">";
        try(var input=new ClassPathResource("seo/about"+(locale.isKorean()?".ko":"")+".html").getInputStream()) {
            html=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("{{CANONICAL}}",canonical).replace("{{DESCRIPTION}}",escape(description)).replace("{{STRUCTURED_DATA}}",json)
                .replace("{{THEME_ID}}",escape(theme.id())).replace("{{THEME_COLOR}}",color)
                .replace("{{APP_STYLE}}",assetUrl("css/app.css")).replace("{{ABOUT_STYLE}}",assetUrl("css/about.css"))
                .replace("{{THEME_STYLE}}",assetUrl("themes/"+theme.directory()+"/theme.css"))
                .replace("{{THEME_HERO}}",assetUrl("themes/"+theme.directory()+"/assets/hero.svg"))
                .replace("{{HEADER_LABEL}}",escape(manifest.path(locale.isKorean()?"copy":"copyEn").path("headerLabel").asText(locale.text("Your video collection","나의 영상 컬렉션"))))
                .replace("{{FOOTER_COPY}}",escape(manifest.path(locale.isKorean()?"copy":"copyEn").path("footer").asText(locale.text("Your videos, your space","당신의 영상, 당신의 공간"))));
        }
    }
    private static String assetUrl(String path)throws IOException {
        try(var input=new ClassPathResource("static/"+path).getInputStream()) {
            return "/"+path+"?v="+ByteCache.tag(input.readAllBytes()).substring(1,17);
        }
    }
    static String validateOrigin(String value) {
        if(value==null||value.isBlank())return "";
        URI uri;
        try{uri=URI.create(value);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid MOVIESITES_PUBLIC_URL");}
        if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/"))||uri.getPort()==0||uri.getPort()>65535)
            throw new IllegalArgumentException("MOVIESITES_PUBLIC_URL must be an HTTP(S) origin without credentials, path, query or fragment");
        return value.endsWith("/")?value.substring(0,value.length()-1):value;
    }
    private static String escape(String text){return text.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
    @GetMapping(value="/about",produces="text/html;charset=UTF-8") public ResponseEntity<String> about(){
        return ResponseEntity.ok().header("Content-Language",locale.id()).header("X-Robots-Tag","index, follow").header("Content-Security-Policy",csp).cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic().mustRevalidate()).eTag("W/"+ByteCache.tag(html.getBytes(StandardCharsets.UTF_8))).body(html);
    }
    @GetMapping(value="/robots.txt",produces="text/plain;charset=UTF-8") public ResponseEntity<String> robots(){
        String body="User-agent: *\nAllow: /\n"+(origin.isEmpty()?"":"Sitemap: "+origin+"/sitemap.xml\n");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic()).body(body);
    }
    @GetMapping(value="/sitemap.xml",produces="application/xml;charset=UTF-8") public ResponseEntity<String> sitemap(){
        if(origin.isEmpty())return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body("<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>Configure MOVIESITES_PUBLIC_URL</error>");
        String body="<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>"+escape(origin+"/about")+"</loc></url></urlset>";
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic().mustRevalidate()).eTag("W/"+ByteCache.tag(body.getBytes(StandardCharsets.UTF_8))).body(body);
    }
}
