package de.financetool.advisory.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Whether the profile minimum for a recommendation topic is met (AD-23): the port the classifier asks, served by
 * {@code profile.ProfileCompleteness} (the single implementation).
 */
public interface ProfileCompleteness {

    /**
     * @param complete whether the minimum for the topic is met
     * @param missingFields the fields still needed, empty if unknown
     * @param limitations the strict FR-17 fields still unknown when the minimum is met, disclosed in the answer
     */
    record Result(boolean complete, List<String> missingFields, List<String> limitations) {

        public Result {
            missingFields = List.copyOf(missingFields);
            limitations = List.copyOf(limitations);
        }

        public Result(boolean complete, List<String> missingFields) {
            this(complete, missingFields, List.of());
        }
    }

    /**
     * @param stored the stored profile
     * @param stated the profile facts the message states (this turn only)
     */
    Result check(Map<String, Object> stored, Map<String, Object> stated, String topic);

    /**
     * The strict FR-17 fields the stored profile lacks for the topic, whether or not its minimum is met (none for the
     * needs topics); none by default.
     */
    default List<String> strictMissing(Map<String, Object> stored, String topic) {
        return List.of();
    }

    /** "Incomplete, no missing fields known". */
    ProfileCompleteness INCOMPLETE = (stored, stated, topic) -> {
        Objects.requireNonNull(topic, "topic");
        return new Result(false, List.of());
    };
}
