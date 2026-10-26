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
 * @param limitations the strict FR-17 fields a {@code RECOMMEND_CATEGORY} turn does not know yet (AD-23)
 */
public record Classification(MessageClass messageClass, List<String> reasons, List<String> missingFields,
        List<Premise> unverifiedPremises, List<String> outOfScope, String topic, List<String> limitations) {

    public Classification {
        Objects.requireNonNull(messageClass, "messageClass");
        Objects.requireNonNull(topic, "topic");
        reasons = List.copyOf(reasons);
        missingFields = List.copyOf(missingFields);
        unverifiedPremises = List.copyOf(unverifiedPremises);
        outOfScope = List.copyOf(outOfScope);
        limitations = List.copyOf(limitations);
    }

    public Classification(MessageClass messageClass, List<String> reasons, List<String> missingFields,
            List<Premise> unverifiedPremises, List<String> outOfScope, String topic) {
        this(messageClass, reasons, missingFields, unverifiedPremises, outOfScope, topic, List.of());
    }
}
