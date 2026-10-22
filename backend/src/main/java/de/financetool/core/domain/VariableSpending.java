package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code variableSpending}: the mean monthly variable spending of the last three complete months. Pure.
 * Variable spending is every debit on the current account that belongs to no standing order or direct debit
 * ({@code recurrenceId}), is no premium, no savings plan payment and no transfer between own accounts.
 */
public final class VariableSpending {

    public static final String FUNCTION = "variableSpending";
    public static final String OUTPUT = "monthly";
    static final int MONTHS = 3;

    private VariableSpending() {
    }

    /** The last calendar month that is complete at the end of {@code asOf}. */
    public static YearMonth lastCompleteMonth(LocalDate asOf) {
        YearMonth month = YearMonth.from(asOf);
        return asOf.equals(month.atEndOfMonth()) ? month : month.minusMonths(1);
    }

    static boolean isVariable(LedgerEntry entry) {
        return entry.isDebit() && entry.recurrenceId() == null && !entry.premium()
                && !LedgerEntry.TRANSFER.equals(entry.category())
                && !LedgerEntry.SAVINGS_PLAN.equals(entry.category());
    }

    /** Variable spending in {@code month}, as a positive amount. */
    static BigDecimal in(List<LedgerEntry> currentAccount, YearMonth month) {
        return currentAccount.stream()
                .filter(VariableSpending::isVariable)
                .filter(e -> YearMonth.from(e.date()).equals(month))
                .map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .negate();
    }

    /** @param currentAccount the current account's bookings up to {@code asOf} */
    public static CoreComputation monthly(List<LedgerEntry> currentAccount, LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        YearMonth last = lastCompleteMonth(asOf);
        YearMonth first = last.minusMonths(MONTHS - 1);
        List<FigureInput> inputs = new ArrayList<>();
        List<BigDecimal> perMonth = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            BigDecimal spent = money(in(currentAccount, month));
            perMonth.add(spent);
            sum = sum.add(spent);
            inputs.add(FigureInput.number("Variable spending in " + Wording.month(month), spent, Unit.EUR));
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(MONTHS), 2, RoundingMode.HALF_UP);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("firstMonth", first.toString());
        keyInputs.put("perMonth", perMonth);

        return new CoreComputation(FUNCTION, "average monthly variable spending",
                "Based on your current account bookings from " + Wording.month(first) + " to "
                        + Wording.month(last) + ".",
                keyInputs, inputs,
                List.of(new Figure(OUTPUT, "Average monthly variable spending", mean, Unit.EUR)),
                List.of("Variable spending is everything you paid from your current account that is not a regular"
                                + " payment, an insurance premium, a savings plan payment or a transfer between your own"
                                + " accounts.",
                        "Uses the average of the last " + MONTHS + " complete months (" + Wording.month(first)
                                + " to " + Wording.month(last) + ")."),
                Map.of());
    }

    static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
