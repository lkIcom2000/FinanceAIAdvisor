package de.financetool.monitoring.domain;

import de.financetool.actions.domain.ActionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A Trigger found by {@link TriggerDetection} with everything the run writes for it, unless its key
 * {@code (type, subjectId, period)} already exists for the persona.
 *
 * @param action the Action to propose, or {@code null}
 * @param escalation the advisor case to open, or {@code null}
 * @param notifications at most one with outcome {@code ACTION}, and only together with an Action
 */
public record DetectedTrigger(TriggerType type, String subjectId, String period, PlannedAction action,
        PlannedEscalation escalation, List<PlannedNotification> notifications) {

    /** An Action for {@code Actions.propose}; its regulatory class is the type's. */
    public record PlannedAction(ActionType type, BigDecimal amount, UUID sourceAccountId, UUID targetAccountId) {

        public PlannedAction {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(amount, "amount");
        }
    }

    /** A {@code TRIGGER} case: the summary for the advisor and the reason codes. */
    public record PlannedEscalation(String summary, List<String> reasons) {

        public PlannedEscalation {
            Objects.requireNonNull(summary, "summary");
            reasons = List.copyOf(reasons);
        }
    }

    /** A templated Notification; an {@code ACTION} one is linked to the Trigger's Action. */
    public record PlannedNotification(NotificationOutcome outcome, String title, String body,
            List<String> citedProvIds) {

        public PlannedNotification {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            citedProvIds = List.copyOf(citedProvIds);
        }
    }

    public DetectedTrigger {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(period, "period");
        notifications = List.copyOf(notifications);
        long actionNotes = notifications.stream().filter(n -> n.outcome() == NotificationOutcome.ACTION).count();
        if (actionNotes != (action == null ? 0 : 1)) {
            throw new IllegalArgumentException("an Action comes with exactly one ACTION notification");
        }
        boolean escalatedNote = notifications.stream().anyMatch(n -> n.outcome() == NotificationOutcome.ESCALATED);
        if ((escalation != null) != escalatedNote) {
            throw new IllegalArgumentException("an escalation comes with an ESCALATED notification");
        }
    }
}
