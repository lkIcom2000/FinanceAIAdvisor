package de.financetool.profile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Whether the profile minimum for a recommendation topic is met (AD-23): the single implementation, used by chat,
 * gate and UI. A pure port of {@code completeness} in {@code experiments/finlab/profile.py}: the effective profile
 * (stored, overridden by the facts stated in the message for this turn only) is checked against the topic's groups,
 * AND over groups, OR within a group.
 */
public final class ProfileCompleteness {

    /** The missing field of a topic without minimums: no personal recommendation exists for it. */
    public static final String TOPIC_NOT_SUPPORTED = "topic_not_supported_for_personal_recommendation";

    /**
     * @param complete whether the minimum for the topic is met
     * @param missingFields the unmet groups, each as {@code a or b}
     * @param limitations the strict FR-17 fields the effective profile still lacks, disclosed when complete
     */
    public record Result(boolean complete, List<String> missingFields, List<String> limitations) {

        public Result {
            missingFields = List.copyOf(missingFields);
            limitations = List.copyOf(limitations);
        }
    }

    private final Map<String, List<List<String>>> topics;
    private final List<String> strictFields;

    public ProfileCompleteness(Map<String, List<List<String>>> topics, List<String> strictFields) {
        this.topics = Map.copyOf(topics);
        this.strictFields = List.copyOf(strictFields);
    }

    public Result evaluate(Map<String, Object> stored, Map<String, Object> stated, String topic) {
        Objects.requireNonNull(topic, "topic");
        Map<String, Object> profile = effective(stored, stated);
        List<List<String>> groups = topics.get(topic);
        if (groups == null) {
            return new Result(false, List.of(TOPIC_NOT_SUPPORTED), List.of());
        }
        List<String> missing = new ArrayList<>();
        for (List<String> group : groups) {
            if (group.stream().noneMatch(field -> present(profile.get(field)))) {
                missing.add(String.join(" or ", group));
            }
        }
        List<String> limitations = !missing.isEmpty() || ProfileFields.NEEDS_TOPICS.contains(topic) ? List.of()
                : strictFields.stream().filter(field -> !present(profile.get(field))).toList();
        return new Result(missing.isEmpty(), missing, limitations);
    }

    /**
     * The strict FR-17 fields the stored profile lacks, whether or not the topic's minimum is met: what a persona
     * workspace still needs before a recommendation (FR-17). None for the needs topics, and none for a topic without
     * minimums (no personal recommendation exists for it).
     */
    public List<String> strictMissing(Map<String, Object> stored, String topic) {
        Objects.requireNonNull(topic, "topic");
        if (!topics.containsKey(topic) || ProfileFields.NEEDS_TOPICS.contains(topic)) {
            return List.of();
        }
        Map<String, Object> profile = stored == null ? Map.of() : stored;
        return strictFields.stream().filter(field -> !present(profile.get(field))).toList();
    }

    /** {@code effective_profile}: stated facts override stored ones for this turn only; nothing is persisted. */
    public static Map<String, Object> effective(Map<String, Object> stored, Map<String, Object> stated) {
        Map<String, Object> out = new LinkedHashMap<>(stored == null ? Map.of() : stored);
        if (stated != null) {
            stated.forEach((field, value) -> {
                if (present(value)) {
                    out.put(field, value);
                }
            });
        }
        return out;
    }

    /** Not {@code None}, {@code ""}, {@code []} or {@code {}}. */
    public static boolean present(Object value) {
        return value != null && !(value instanceof String s && s.isEmpty())
                && !(value instanceof Collection<?> c && c.isEmpty()) && !(value instanceof Map<?, ?> m && m.isEmpty());
    }
}
