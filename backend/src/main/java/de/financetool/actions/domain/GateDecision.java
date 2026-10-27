package de.financetool.actions.domain;

import java.util.List;
import java.util.Objects;

/**
 * The gate's decision.
 *
 * @param level the maximum autonomy level: the lowest cap of the failed criteria
 * @param cappingCriteria every failed criterion id in table order (not only the lowest), or {@code not_in_l3_set}
 */
public record GateDecision(AutonomyLevel level, List<String> cappingCriteria) {

    public GateDecision {
        Objects.requireNonNull(level, "level");
        cappingCriteria = List.copyOf(cappingCriteria);
    }
}
