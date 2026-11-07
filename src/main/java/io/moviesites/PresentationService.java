package io.moviesites;

import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Startup presentation settings; media names and paths remain independent of themes. */
@Service
public class PresentationService {
    public record Theme(String id, String name, String group, String directory) {}
    private final Theme theme;
    private final Map<String, String> folderLabels;

    public PresentationService(@Value("${moviesites.theme}") String selected,
            @Value("${moviesites.folder-labels}") String labels) throws IOException {
        var json = new JsonMapper();
        JsonNode catalog;
        try (var input = new ClassPathResource("static/themes/catalog.json").getInputStream()) {
            catalog = json.readTree(input);
        }
        var themes = new LinkedHashMap<String, Theme>();
        for (JsonNode item : catalog.path("themes")) {
            String id = item.path("id").asText(), directory = item.path("directory").asText();
            if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || !directory.matches("(?:[1-6]0s/)?[a-z0-9]+(?:-[a-z0-9]+)*")
                    || themes.containsKey(id)) throw new IllegalArgumentException("Invalid packaged theme catalog");
            themes.put(id, new Theme(id, item.path("name").asText(), item.path("group").asText(), directory));
        }
        theme = themes.get(selected);
        if (theme == null) throw new IllegalArgumentException("Unknown MOVIESITES_THEME: choose an ID from /themes/catalog.json");
        if (labels.length() > 8192) throw new IllegalArgumentException("MOVIESITES_FOLDER_LABELS is too large");
        JsonNode values;
        try { values = json.readTree(labels); }
        catch (RuntimeException e) { throw new IllegalArgumentException("MOVIESITES_FOLDER_LABELS must be a JSON object", e); }
        if (values == null || !values.isObject() || values.size() > 32)
            throw new IllegalArgumentException("MOVIESITES_FOLDER_LABELS must be a JSON object with at most 32 labels");
        var parsed = new LinkedHashMap<String, String>();
        for (var entry : values.properties()) {
            String key = entry.getKey(); JsonNode value = entry.getValue();
            if (key.isBlank() || key.length() > 255 || key.contains("/") || key.contains("\\")
                    || !value.isString() || value.asText().isBlank() || value.asText().length() > 255
                    || key.codePoints().anyMatch(Character::isISOControl)
                    || value.asText().codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid MOVIESITES_FOLDER_LABELS entry");
            parsed.put(key, value.asText());
        }
        folderLabels = Collections.unmodifiableMap(parsed);
    }

    public Theme theme() { return theme; }
    public Map<String, String> folderLabels() { return folderLabels; }
}
