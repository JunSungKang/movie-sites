package io.moviesites;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/random-playback")
public class RandomPlaybackController {
    private final RandomPlayback random;
    public RandomPlaybackController(RandomPlayback random) { this.random = random; }
    @GetMapping public RandomPlayback.Page random(@RequestParam(defaultValue="") String path,
            @RequestParam int minutes, @RequestParam(required=false) String query,
            @RequestParam(defaultValue="false") boolean refresh) {
        return random.request(path, minutes, query, refresh);
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestParam(defaultValue="") String path, @RequestParam int minutes, @RequestParam String query) {
        random.cancel(path, minutes, query);
    }
}
