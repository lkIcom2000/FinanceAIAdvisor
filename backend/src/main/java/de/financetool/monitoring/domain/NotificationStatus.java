package de.financetool.monitoring.domain;

import de.financetool.actions.domain.ActionState;
import java.util.Objects;

/** The status chip of a Notification, derived from its outcome and, for {@code ACTION}, its Action's state (AD-10). */
public enum NotificationStatus {
    FOR_YOUR_INFORMATION, RECOMMENDATION, NEEDS_YOUR_OK, DONE_FOR_YOU, UNDONE, WITH_AN_ADVISOR;

    /**
     * @param actionState the linked Action's state; read only for outcome {@code ACTION}
     * @throws IllegalArgumentException for outcome {@code ACTION} without an Action state
     */
    public static NotificationStatus of(NotificationOutcome outcome, ActionState actionState) {
        Objects.requireNonNull(outcome, "outcome");
        return switch (outcome) {
            case INFORMED -> FOR_YOUR_INFORMATION;
            case RECOMMENDED -> RECOMMENDATION;
            case ESCALATED -> WITH_AN_ADVISOR;
            case ACTION -> {
                if (actionState == null) {
                    throw new IllegalArgumentException("an ACTION notification needs its Action's state");
                }
                yield switch (actionState) {
                    case INFORMED -> FOR_YOUR_INFORMATION;
                    case RECOMMENDED -> RECOMMENDATION;
                    case AWAITING_CONFIRMATION -> NEEDS_YOUR_OK;
                    case EXECUTED -> DONE_FOR_YOU;
                    case ESCALATED -> WITH_AN_ADVISOR;
                    case UNDONE -> UNDONE;
                };
            }
        };
    }
}
