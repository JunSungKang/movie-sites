package io.moviesites;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Optional external metadata overrides conservative, explicitly labelled title suggestions. */
@Service
public class VideoTopics {
    public record Topics(List<String> tags, String source, boolean invalidMetadata) {}
    private record Rule(String label, Pattern words) {}
    private static Rule rule(String label, String words) { return new Rule(label, Pattern.compile(words, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)); }
    private static final List<Rule> RULES = List.of(
        rule("예절교육", "예절|인사|고마워|감사|미안|양보|차례|manners|please|thank"),
        rule("식습관교육", "야채|채소|과일|음식|식사|요리|편식|빵|주스|nutrition|meal|vegetable"),
        rule("배변교육", "응가|변기|화장실|배변|potty|toilet"),
        rule("놀이교육", "놀이|스케이트|캠핑|물놀이|공놀이|play"),
        rule("사교성교육", "친구|함께|나눠|서로|협동|friend|sharing"),
        rule("안전교육", "구조|구급|안전|소방|헬기|rescue|safety"),
        rule("생활습관교육", "아침|씻|양치|목욕|정리|생활|morning|brush"),
        rule("건강교육", "병원|의사|doctor|hospital"),
        rule("기초학습", "숫자|열 마리|색깔|알록달록|numbers|count"),
        rule("동물·자연", "나비|동물|숲|상어|돼지|nature"),
        rule("노래·율동", "뮤직비디오|노래|동요|오프닝|송$|song|dance")
    );
    static final int MAX_BYTES = 8192;
    private final Storage storage;
    private final JsonMapper json = new JsonMapper();
    public VideoTopics(Storage storage) { this.storage = storage; }

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
        var tags = RULES.stream().filter(rule -> rule.words().matcher(name).find()).map(Rule::label).limit(3).toList();
        return new Topics(tags, tags.isEmpty() ? "none" : "filename", invalid);
    }
}
