package xyz.gg_jskang.moviesites.media;
import xyz.gg_jskang.moviesites.configuration.Settings;
import xyz.gg_jskang.moviesites.presentation.UiLocale;
import xyz.gg_jskang.moviesites.storage.TestStorage;
public final class TestMedia {
    private TestMedia() {}
    public static MediaService create(Settings settings,TestStorage storage){return new MediaService(settings,storage,new UiLocale("en"),storage.workspace());}
}
