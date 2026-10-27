package de.financetool.actions;

import java.math.BigDecimal;

/**
 * Module API: an undone transfer.
 *
 * @param backInOvernight whether the money went back to the overnight deposit (else to the current account)
 */
public record ActionUndo(BigDecimal amount, boolean backInOvernight) {
}
