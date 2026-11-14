package io.moviesites;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.*;
import org.springframework.core.io.ClassPathResource;

/** Immutable only when the requested fingerprint matches the actual packaged bytes. */
final class StaticAssets {
    private final UiLocale locale;
    StaticAssets() { this(new UiLocale("en")); }
    StaticAssets(UiLocale locale) { this.locale = locale; }
    private final ByteCache cache=new ByteCache(256,8*1024*1024,32);
    private static final Map<String,String> TYPES=Map.of("js","text/javascript;charset=UTF-8","css","text/css;charset=UTF-8","json","application/json;charset=UTF-8","svg","image/svg+xml","png","image/png","webmanifest","application/manifest+json;charset=UTF-8");
    boolean serve(HttpServletRequest request,HttpServletResponse response) throws IOException {
        if(!request.getMethod().equals("GET")&&!request.getMethod().equals("HEAD"))return false;
        String path=request.getRequestURI();
        if (path.equals("/") || path.equals("/index.html") || path.equals("/themes/preview.html")) {
            String page = path.equals("/themes/preview.html") ? "themes/preview" : "index";
            var resource = new ClassPathResource("static/" + page + (locale.isKorean() ? ".ko" : "") + ".html");
            ByteCache.Result result = cache.get(resource.getPath(),31536000,()->ByteCache.read(resource.getInputStream(),1024*1024));
            response.setHeader("Cache-Control","no-store");
            response.setHeader("Content-Language",locale.id());
            response.setContentType("text/html;charset=UTF-8");
            response.setContentLength(result.value().bytes().length);
            if (!request.getMethod().equals("HEAD")) response.getOutputStream().write(result.value().bytes());
            return true;
        }
        boolean worker=path.equals("/service-worker.js"), manifest=path.equals("/manifest.webmanifest");
        if(!(worker||path.equals("/manifest.webmanifest")||path.matches("/(?:js|css|themes|seo|pwa)/[a-zA-Z0-9_./-]+\\.(?:js|css|json|svg|png)"))||path.contains("..")||path.contains("//"))return false;
        String selected=manifest&&locale.isKorean()?"/manifest.ko.webmanifest":path;
        var resource=new ClassPathResource("static"+selected);if(!resource.exists())return false;
        ByteCache.Result result=cache.get(selected,31536000,()->ByteCache.read(resource.getInputStream(),1024*1024));
        String digest=result.value().etag(), version=request.getParameter("v");
        boolean fingerprint=!worker&&!manifest&&version!=null&&(version.equals(digest.substring(1,17))||version.equals(digest.substring(1,digest.length()-1)));
        if(worker)response.setHeader("Service-Worker-Allowed","/");
        if(manifest)response.setHeader("Content-Language",locale.id());
        response.setHeader("Cache-Control",fingerprint?"public, max-age=31536000, immutable":"public, no-cache");
        response.setHeader("ETag","W/"+digest);response.setHeader("Vary","Accept-Encoding");
        response.setContentType(TYPES.get(path.substring(path.lastIndexOf('.')+1)));
        if(matches(request.getHeader("If-None-Match"),digest)){response.setStatus(304);return true;}
        response.setContentLength(result.value().bytes().length);
        if(!request.getMethod().equals("HEAD"))response.getOutputStream().write(result.value().bytes());
        return true;
    }
    static boolean matches(String header,String etag){if(header==null)return false;for(String value:header.split(",")){value=value.strip();if(value.equals("*")||value.equals(etag)||value.equals("W/"+etag))return true;}return false;}
}
