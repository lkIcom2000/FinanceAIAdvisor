package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
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
 * Core function {@code emergencyFundTarget}: the emergency fund (the overnight deposit balance) against its target of
 * {@value #MONTHS_MIN} to {@value #MONTHS_MAX} months of expenses. Monthly expenses are the mean of the current
 * account's debits over the last 12 complete months, without transfers between own accounts and savings plans. Pure.
 */
public final class EmergencyFundTarget {

    public static final String FUNCTION = "emergencyFundTarget";
    public static final String MONTHS_MIN = "emergency_fund_months_min";
    public static final String MONTHS_MAX = "emergency_fund_months_max";
    public static final String OUTPUT_CURRENT = "current";
    public static final String OUTPUT_TARGET = "target";
    public static final String OUTPUT_TARGET_MAX = "targetMax";
    public static final String OUTPUT_MONTHS = "months";
    public static final String OUTPUT_MONTHLY_EXPENSES = "monthlyExpenses";
    static final int EXPENSE_MONTHS = 12;

    private EmergencyFundTarget() {
    }

    static boolean isExpense(LedgerEntry entry) {
        return entry.isDebit() && !LedgerEntry.TRANSFER.equals(entry.category())
                && !LedgerEntry.SAVINGS_PLAN.equals(entry.category());
    }

    /**
     * @param overnightBalance the overnight deposit balance (a Core figure), or {@code null} without an overnight
     *        deposit (the fund is then 0.00)
     * @param currentAccount the current account's bookings up to {@code asOf}
     */
    public static CoreComputation of(SourcedNumber overnightBalance, List<LedgerEntry> currentAccount,
            LocalDate asOf, RuleParameterValue monthsMin, RuleParameterValue monthsMax) {
        Objects.requireNonNull(asOf, "asOf");
        requireKey(monthsMin, MONTHS_MIN);
        requireKey(monthsMax, MONTHS_MAX);
        YearMonth last = VariableSpending.lastCompleteMonth(asOf);
        YearMonth first = last.minusMonths(EXPENSE_MONTHS - 1);
        BigDecimal debits = currentAccount.stream()
                .filter(EmergencyFundTarget::isExpense)
                .filter(e -> {
                    YearMonth month = YearMonth.from(e.date());
                    return !month.isBefore(first) && !month.isAfter(last);
                })
                .map(LedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .negate();
        BigDecimal expenses = debits.divide(BigDecimal.valueOf(EXPENSE_MONTHS), 2, RoundingMode.HALF_UP);
        BigDecimal current = overnightBalance == null ? new BigDecimal("0.00") : overnightBalance.value();
        BigDecimal target = money(expenses.multiply(monthsMin.value()));
        BigDecimal targetMax = money(expenses.multiply(monthsMax.value()));
        String months = monthsMin.value().stripTrailingZeros().toPlainString();
        String monthsMaxText = monthsMax.value().stripTrailingZeros().toPlainString();

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("current", current);
        keyInputs.put("expenseMonths", first + "/" + last);
        keyInputs.put("expensesTotal", money(debits));
        keyInputs.put("monthsMin", monthsMin.value());
        keyInputs.put("monthsMinVersion", monthsMin.version());
        keyInputs.put("monthsMax", monthsMax.value());
        keyInputs.put("monthsMaxVersion", monthsMax.version());

        List<FigureInput> inputs = new ArrayList<>();
        inputs.add(overnightBalance == null
                ? FigureInput.text("Overnight deposit", "You don't have an overnight deposit.")
                : FigureInput.figure("Overnight deposit balance on " + Wording.date(asOf), overnightBalance));
        inputs.add(FigureInput.number("Money out of your current account from " + Wording.month(first) + " to "
                + Wording.month(last), money(debits), Unit.EUR));
        inputs.add(FigureInput.number("Months of expenses (minimum)", monthsMin.value(), Unit.MONTHS));
        inputs.add(FigureInput.number("Months of expenses (comfortable)", monthsMax.value(), Unit.MONTHS));

        return new CoreComputation(FUNCTION, "emergency fund target",
                "Based on your account data from " + Wording.month(first) + " to " + Wording.date(asOf) + ".",
                keyInputs, inputs,
                List.of(new Figure(OUTPUT_CURRENT, "Emergency fund", current, Unit.EUR),
                        new Figure(OUTPUT_TARGET, "Emergency fund target (" + months + " months of expenses)", target,
                                Unit.EUR),
                        new Figure(OUTPUT_TARGET_MAX, "Comfortable emergency fund (" + monthsMaxText
                                + " months of expenses)", targetMax, Unit.EUR),
                        new Figure(OUTPUT_MONTHS, "Months of expenses in the target", monthsMin.value(),
                                Unit.MONTHS),
                        new Figure(OUTPUT_MONTHLY_EXPENSES, "Average monthly expenses", expenses, Unit.EUR)),
                List.of("Your emergency fund is the money in your overnight deposit.",
                        "Monthly expenses are the average of everything paid from your current account over the last "
                                + EXPENSE_MONTHS + " complete months, without transfers between your own accounts"
                                + " and savings plans.",
                        "A good emergency fund covers " + months + " to " + monthsMaxText + " months of expenses."),
                Map.of(monthsMin.key(), monthsMin.version(), monthsMax.key(), monthsMax.version()));
    }

    private static void requireKey(RuleParameterValue parameter, String key) {
        Objects.requireNonNull(parameter, key);
        if (!key.equals(parameter.key())) {
            throw new IllegalArgumentException("expected Rule Parameter " + key + ", got " + parameter.key());
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
