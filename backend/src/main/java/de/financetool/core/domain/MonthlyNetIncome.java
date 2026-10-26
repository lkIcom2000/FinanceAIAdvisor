package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code monthlyNetIncome}: the mean monthly salary paid into the current account over the last
 * {@value #MONTHS} complete months, the assessment card's income pre-fill (Story 6.2). Salary is every credit with
 * the category {@value #SALARY}. Pure.
 */
public final class MonthlyNetIncome {

    public static final String FUNCTION = "monthlyNetIncome";
    public static final String OUTPUT = "monthly";
    public static final String SALARY = "SALARY";
    static final int MONTHS = 12;

    private MonthlyNetIncome() {
    }

    /** @param currentAccount the current account's bookings up to {@code asOf} */
    public static CoreComputation of(List<LedgerEntry> currentAccount, LocalDate asOf) {
        Objects.requireNonNull(asOf, "asOf");
        YearMonth last = VariableSpending.lastCompleteMonth(asOf);
        YearMonth first = last.minusMonths(MONTHS - 1);
        BigDecimal salary = currentAccount.stream()
                .filter(e -> !e.isDebit() && SALARY.equals(e.category()))
                .filter(e -> {
                    YearMonth month = YearMonth.from(e.date());
                    return !month.isBefore(first) && !month.isAfter(last);
                })
                .map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = VariableSpending.money(salary);
        BigDecimal mean = total.divide(BigDecimal.valueOf(MONTHS), 2, RoundingMode.HALF_UP);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("firstMonth", first.toString());
        keyInputs.put("salaryTotal", total);

        return new CoreComputation(FUNCTION, "average monthly net income",
                "Based on the salary paid into your current account from " + Wording.month(first) + " to "
                        + Wording.month(last) + ".",
                keyInputs,
                List.of(FigureInput.number("Salary received from " + Wording.month(first) + " to "
                        + Wording.month(last), total, Unit.EUR)),
                List.of(new Figure(OUTPUT, "Average monthly net income", mean, Unit.EUR)),
                List.of("Net income is the salary paid into your current account; other money coming in is not"
                                + " counted.",
                        "Uses the average of the last " + MONTHS + " complete months (" + Wording.month(first)
                                + " to " + Wording.month(last) + ")."),
                Map.of());
    }
}
