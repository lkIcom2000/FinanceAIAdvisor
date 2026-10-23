package de.financetool.advisory.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Whether the profile minimum for a recommendation topic is met (AD-23): the port the classifier asks. Story 6.1
 * supplies {@code profile.ProfileCompleteness}; until then a stub reports "incomplete" with no missing fields.
 */
public interface ProfileCompleteness {

    /**
     * @param complete whether the minimum for the topic is met
     * @param missingFields the fields still needed, empty if unknown
     */
    record Result(boolean complete, List<String> missingFields) {

        public Result {
            missingFields = List.copyOf(missingFields);
        }
    }

    /**
     * @param stored the stored profile
     * @param stated the profile facts the message states (this turn only)
     */
    Result check(Map<String, Object> stored, Map<String, Object> stated, String topic);

    /** "Incomplete, no missing fields known": the answer until Story 6.1. */
    ProfileCompleteness INCOMPLETE = (stored, stated, topic) -> {
        Objects.requireNonNull(topic, "topic");
        return new Result(false, List.of());
    };
}
