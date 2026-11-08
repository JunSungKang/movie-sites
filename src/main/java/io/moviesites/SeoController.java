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
    private static final String DESCRIPTION="내 영상 폴더를 그대로 탐색하고, 길이와 주제로 골라 보는 오픈소스 영상 라이브러리, MovieSites.";
    public SeoController(@Value("${moviesites.public-url:}")String url) throws Exception {
        origin=validateOrigin(url);
        Map<String,Object> data=new LinkedHashMap<>();data.put("@context","https://schema.org");data.put("@type","WebPage");data.put("name","MovieSites — 나의 영상 라이브러리");data.put("description",DESCRIPTION);data.put("inLanguage","ko");
        if(!origin.isEmpty())data.put("url",origin+"/about");
        String json=new JsonMapper().writeValueAsString(data).replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026");
        String hash=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        csp="default-src 'self'; img-src 'self' data:; media-src 'self'; script-src 'self' 'sha256-"+hash+"'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'";
        String canonical=origin.isEmpty()?"":"<link rel=\"canonical\" href=\""+escape(origin+"/about")+"\"><meta property=\"og:url\" content=\""+escape(origin+"/about")+"\">";
        try(var input=new ClassPathResource("seo/about.html").getInputStream()) {
            html=new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("{{CANONICAL}}",canonical).replace("{{DESCRIPTION}}",escape(DESCRIPTION)).replace("{{STRUCTURED_DATA}}",json);
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
        return ResponseEntity.ok().header("X-Robots-Tag","index, follow").header("Content-Security-Policy",csp).cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic().mustRevalidate()).eTag("W/"+ByteCache.tag(html.getBytes(StandardCharsets.UTF_8))).body(html);
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
