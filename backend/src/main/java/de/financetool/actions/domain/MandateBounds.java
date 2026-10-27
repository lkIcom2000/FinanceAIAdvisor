package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The bounds of the Mandate (rule) covering an Action.
 *
 * @param mandateId the rule's id
 * @param maxPerAction the largest amount one Action may move
 * @param maxPerMonth the largest amount the rule's Actions may move in a calendar month
 * @param floor the source account must not fall below this after the Action
 * @param movedThisMonth the amounts of the rule's executed Actions in the calendar month of the simulated date
 */
public record MandateBounds(UUID mandateId, RuleTemplate template, BigDecimal maxPerAction, BigDecimal maxPerMonth,
        BigDecimal floor, BigDecimal movedThisMonth) {

    public MandateBounds {
        Objects.requireNonNull(mandateId, "mandateId");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(maxPerAction, "maxPerAction");
        Objects.requireNonNull(maxPerMonth, "maxPerMonth");
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(movedThisMonth, "movedThisMonth");
    }

    /** Whether moving {@code amount} more would go beyond the month's bound. */
    public boolean aboveMonth(BigDecimal amount) {
        return movedThisMonth.add(amount).compareTo(maxPerMonth) > 0;
    }
}
