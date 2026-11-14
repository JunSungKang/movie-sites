package io.moviesites;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class UiLocaleTests {
    @Test void onlySupportedStartupLanguagesAreAcceptedAndDefaultIsEnglish() {
        for(String selected:List.of("en","ko")) {
            try(var context=new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("locale-test",Map.of("moviesites.ui-locale",selected)));
                context.register(UiLocale.class);context.refresh();
                assertEquals(selected,context.getBean(UiLocale.class).id());
            }
        }
        try(var context=new AnnotationConfigApplicationContext(UiLocale.class)) {
            assertEquals("en",context.getBean(UiLocale.class).id());
        }
        for(String invalid:List.of("", "EN", "en-US", "../ko", "fr"))
            assertThrows(IllegalArgumentException.class,()->new UiLocale(invalid));
    }
    @Test void apiErrorsUseSelectedLanguageAndNeverExposeUnderlyingPaths() {
        for(String selected:List.of("en","ko")) {
            var errors=new WebConfiguration.Errors(new UiLocale(selected));
            var response=errors.error(new NoSuchFileException("/sensitive/original.mp4"));
            assertEquals(404,response.getStatusCode().value());
            assertEquals("NOT_FOUND",response.getBody().get("code"));
            String message=response.getBody().get("message");
            assertFalse(message.contains("/sensitive"));
            assertEquals(selected.equals("ko"),message.codePoints().anyMatch(c->c>=0xAC00&&c<=0xD7A3));
            assertEquals("no-store",response.getHeaders().getCacheControl());
        }
    }
}
