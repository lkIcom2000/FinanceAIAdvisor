package de.financetool.actions.domain;

import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/** The state of an Action row. */
@NamedInterface("types")
public enum ActionState {
    INFORMED, RECOMMENDED, AWAITING_CONFIRMATION, EXECUTED, ESCALATED;

    /** The state a newly proposed Action starts in at its gate level: at L3 Fine executes it within the rule. */
    public static ActionState proposedAt(AutonomyLevel level) {
        Objects.requireNonNull(level, "level");
        return switch (level) {
            case L0 -> INFORMED;
            case L1 -> RECOMMENDED;
            case L2 -> AWAITING_CONFIRMATION;
            case L3 -> EXECUTED;
        };
    }
}
