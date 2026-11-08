package io.moviesites;

import java.io.IOException;
import java.util.Map;
import jakarta.servlet.http.*;
import org.springframework.core.io.ClassPathResource;

/** Immutable only when the requested fingerprint matches the actual packaged bytes. */
final class StaticAssets {
    private final ByteCache cache=new ByteCache(256,8*1024*1024,32);
    private static final Map<String,String> TYPES=Map.of("js","text/javascript;charset=UTF-8","css","text/css;charset=UTF-8","json","application/json;charset=UTF-8","svg","image/svg+xml","png","image/png");
    boolean serve(HttpServletRequest request,HttpServletResponse response) throws IOException {
        if(!request.getMethod().equals("GET")&&!request.getMethod().equals("HEAD"))return false;
        String path=request.getRequestURI();
        if(!path.matches("/(?:js|css|themes|seo)/[a-zA-Z0-9_./-]+\\.(?:js|css|json|svg|png)")||path.contains("..")||path.contains("//"))return false;
        var resource=new ClassPathResource("static"+path);if(!resource.exists())return false;
        ByteCache.Result result=cache.get(path,31536000,()->ByteCache.read(resource.getInputStream(),1024*1024));
        String digest=result.value().etag(), version=request.getParameter("v");
        boolean fingerprint=version!=null&&(version.equals(digest.substring(1,17))||version.equals(digest.substring(1,digest.length()-1)));
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
