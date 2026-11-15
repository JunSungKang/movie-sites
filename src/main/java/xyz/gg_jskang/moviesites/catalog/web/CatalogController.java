package xyz.gg_jskang.moviesites.catalog.web;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import xyz.gg_jskang.moviesites.catalog.CatalogService;
import xyz.gg_jskang.moviesites.support.Hashes;
@RestController
@RequestMapping("/api")
public final class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) {this.catalog=catalog;}
    @GetMapping("/browse") public CompletableFuture<ResponseEntity<Object>> browse(@RequestParam(defaultValue="")String path,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="filesystem")String sort,@RequestParam(required=false)String query,@RequestParam(defaultValue="false")boolean refresh) {
        return catalog.browse(path,cursor,sort,query,refresh).thenApply(result->{
            if(!result.durationOrder()||!result.ready())return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.body());
            return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(result.cacheSeconds())).cachePrivate().mustRevalidate()).eTag("W/\"list-"+Hashes.digest(result.body().toString())+"\"").body(result.body());
        });
    }
}
