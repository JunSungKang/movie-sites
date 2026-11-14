package io.moviesites;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** One startup-selected interface language; independent of the native filename locale. */
@Service
public final class UiLocale {
    private final String id;
    public UiLocale(@Value("${moviesites.ui-locale:en}") String selected) {
        if (!"en".equals(selected) && !"ko".equals(selected))
            throw new IllegalArgumentException("Unknown MOVIESITES_UI_LOCALE: choose en or ko");
        id = selected;
    }
    public String id() { return id; }
    public boolean isKorean() { return id.equals("ko"); }
    public String text(String english, String korean) { return isKorean() ? korean : english; }
}
