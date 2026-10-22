package de.financetool.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One booking as the Core sees it.
 *
 * @param amount positive for money in, negative for money out
 * @param category the transaction category name (e.g. {@code TRANSFER}), or {@code null} if uncategorised
 * @param description the counterparty or reference in plain words
 * @param recurrenceId the monthly standing order or direct debit it belongs to, or {@code null}
 * @param premium whether it is an insurance or pension premium debit of a contract
 */
public record LedgerEntry(LocalDate date, BigDecimal amount, String category, String description,
        String recurrenceId, boolean premium) {

    public static final String TRANSFER = "TRANSFER";
    public static final String SAVINGS_PLAN = "SAVINGS_PLAN";

    public LedgerEntry {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
    }

    boolean isDebit() {
        return amount.signum() < 0;
    }
}
