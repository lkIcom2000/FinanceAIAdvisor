package de.financetool.actions;

import java.math.BigDecimal;

/**
 * Module API: the outcome of a confirmation.
 *
 * @param reason why the transfer was refused, {@code null} once done
 * @param why the "Why?" explanation of a refusal, {@code null} once done
 */
public record ActionConfirmation(boolean done, BigDecimal amount, String reason, String why) {
}
