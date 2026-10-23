package de.financetool.advisory.domain;

import java.util.List;
import java.util.Objects;

/**
 * The class of a message and the facts that led to it ({@code Classification} of
 * {@code experiments/finlab/classify.py}).
 *
 * @param missingFields profile fields an {@code ASK_PROFILE} turn asks for
 * @param unverifiedPremises the premises that made the turn {@code DETECT_TRAP}
 * @param outOfScope the out-of-scope product categories of a {@code REFUSE_OUT_OF_SCOPE} turn, sorted
 */
public record Classification(MessageClass messageClass, List<String> reasons, List<String> missingFields,
        List<Premise> unverifiedPremises, List<String> outOfScope, String topic) {

    public Classification {
        Objects.requireNonNull(messageClass, "messageClass");
        Objects.requireNonNull(topic, "topic");
        reasons = List.copyOf(reasons);
        missingFields = List.copyOf(missingFields);
        unverifiedPremises = List.copyOf(unverifiedPremises);
        outOfScope = List.copyOf(outOfScope);
    }
}
