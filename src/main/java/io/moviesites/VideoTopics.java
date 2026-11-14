package io.moviesites;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Optional external metadata overrides conservative, explicitly labelled title suggestions. */
@Service
public class VideoTopics {
    public record Topics(List<String> tags, String source, boolean invalidMetadata) {}
    private record Rule(String label, Pattern words) {}
    static final int MAX_BYTES = 8192;
    private final Storage storage;
    private final List<Rule> rules;
    private final JsonMapper json = new JsonMapper();
    /** Programmatic callers use the same neutral profile as the default configuration. */
    public VideoTopics(Storage storage) { this(storage, "none", new UiLocale("en")); }
    public VideoTopics(Storage storage, String profile) { this(storage, profile, new UiLocale("en")); }

    @Autowired
    public VideoTopics(Storage storage, @Value("${moviesites.topic-profile:none}") String profile, UiLocale locale) {
        this.storage = storage;
        rules = loadProfile(profile, locale);
    }

    private List<Rule> loadProfile(String profile, UiLocale locale) {
        if ("none".equals(profile)) return List.of();
        if (!"kids-education".equals(profile))
            throw new IllegalArgumentException("Unknown MOVIESITES_TOPIC_PROFILE: choose none or kids-education");
        // Profile IDs are allowlisted before constructing a resource path; external paths are never loaded.
        try (var input = new ClassPathResource("topic-profiles/kids-education.json").getInputStream()) {
            var node = json.readTree(input);
            if (node == null || !node.path("rules").isArray() || node.path("rules").isEmpty() || node.path("rules").size() > 32)
                throw new IllegalArgumentException("Invalid packaged topic profile");
            var parsed = new ArrayList<Rule>();
            for (var item : node.path("rules")) {
                var label = item.path(locale.isKorean() ? "labelKo" : "label"); var pattern = item.path("pattern");
                if (!label.isString() || label.asText().isBlank() || label.asText().length() > 40
                        || label.asText().codePoints().anyMatch(Character::isISOControl)
                        || !pattern.isString() || pattern.asText().isBlank() || pattern.asText().length() > 512)
                    throw new IllegalArgumentException("Invalid packaged topic rule");
                parsed.add(new Rule(label.asText(), Pattern.compile(pattern.asText(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
            }
            return List.copyOf(parsed);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load packaged topic profile", error);
        }
    }

    public Topics read(String path) {
        boolean invalid = false;
        try (var channel = storage.open(storage.normalize(path) + ".json"); var input = Channels.newInputStream(channel)) {
            if (channel.size() > MAX_BYTES) throw new IllegalArgumentException("Metadata too large");
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Metadata too large");
            var node = json.readTree(bytes);
            if (node == null || !node.isObject() || !node.path("tags").isArray() || node.path("tags").size() > 8)
                throw new IllegalArgumentException("Expected a bounded tags array");
            var tags = new LinkedHashSet<String>();
            for (var tag : node.path("tags")) {
                if (!tag.isString() || tag.asText().isBlank() || tag.asText().length() > 40 || tag.asText().codePoints().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("Invalid topic label");
                tags.add(tag.asText().strip());
            }
            return new Topics(List.copyOf(tags), "metadata", false);
        } catch (NoSuchFileException absent) {
            // A missing optional sidecar is normal. Secure storage also refuses symlink targets.
        } catch (IOException | RuntimeException malformed) { invalid = true; }
        String name = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
        var tags = rules.stream().filter(rule -> rule.words().matcher(name).find()).map(Rule::label).limit(3).toList();
        return new Topics(tags, tags.isEmpty() ? "none" : "filename", invalid);
    }
}
