package xyz.gg_jskang.moviesites.system.web;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.presentation.PresentationService;
import xyz.gg_jskang.moviesites.storage.MediaStore;
@RestController
@RequestMapping("/api")
public final class SystemController {
    private final Settings settings;
    private final MediaStore storage;
    private final PresentationService presentation;
    private final String instanceId=java.util.UUID.randomUUID().toString();
    public SystemController(Settings settings,MediaStore storage,PresentationService presentation){this.settings=settings;this.storage=storage;this.presentation=presentation;}
    @GetMapping("/health") public Map<String,Object> health(){return Map.of("status","UP","application","MovieSites");}
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("rootId",storage.rootId(),"instanceId",instanceId,"cacheTtlSeconds",settings.browserCacheTtlSeconds(),"pageSize",settings.pageSize(),"sourceUrl","https://github.com/JunSungKang/movie-sites","license","AGPL-3.0-only","theme",presentation.theme(),"folderLabels",presentation.folderLabels(),"locale",presentation.uiLocale().id());}
}
