package de.financetool.actions;

import java.math.BigDecimal;

/**
 * Module API: the L2 dialog's content (FR-39, FR-40): the exact transfer, both accounts before and after it (from the
 * Core), and why it needs the user's OK.
 *
 * @param why the fixed "Why?" explanation of the reason
 */
public record ActionReview(BigDecimal amount, String fromName, BigDecimal fromBefore, BigDecimal fromAfter,
        String toName, BigDecimal toBefore, BigDecimal toAfter, String reason, String why) {
}
