package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code withdrawalDuration}: how many full month-end withdrawals a portfolio pays out under two rate
 * conventions (FR-20). Pure. A port of {@code withdrawal_duration} in {@code experiments/finlab/core.py}, with the
 * same floating-point steps, so Q&amp;A item 40 gives 522 and 538.
 */
public final class WithdrawalDuration {

    public static final String FUNCTION = "withdrawalDuration";
    public static final String PORTFOLIO = "portfolio";
    public static final String MONTHLY_WITHDRAWAL = "monthlyWithdrawal";
    public static final String ANNUAL_RATE = "annualRate";
    public static final String FULL_EFFECTIVE = "fullWithdrawalsEffective";
    public static final String YEARS_EFFECTIVE = "yearsEffective";
    public static final String FINAL_EFFECTIVE = "finalWithdrawalEffective";
    public static final String FULL_NOMINAL = "fullWithdrawalsNominal";
    public static final String YEARS_NOMINAL = "yearsNominal";
    public static final String FINAL_NOMINAL = "finalWithdrawalNominal";

    /** The count the experiment reports when the monthly return covers the withdrawal. */
    static final int PERPETUAL = 1_000_000;

    private WithdrawalDuration() {
    }

    /**
     * @param portfolio the starting portfolio in EUR
     * @param portfolioFigure the Core figure the portfolio is (account data), or {@code null} if it was stated
     * @param monthlyWithdrawal the withdrawal at each month end in EUR
     * @param annualRate the assumed annual return as a fraction (0.04 = 4 %)
     */
    public static CoreComputation compute(BigDecimal portfolio, SourcedNumber portfolioFigure,
            BigDecimal monthlyWithdrawal, BigDecimal annualRate) {
        Objects.requireNonNull(portfolio, "portfolio");
        Objects.requireNonNull(monthlyWithdrawal, "monthlyWithdrawal");
        Objects.requireNonNull(annualRate, "annualRate");
        if (monthlyWithdrawal.signum() <= 0 || portfolio.signum() < 0) {
            throw new IllegalArgumentException("a positive withdrawal from a portfolio of at least zero");
        }
        double rate = annualRate.doubleValue();
        Count effective = count(portfolio.doubleValue(), monthlyWithdrawal.doubleValue(),
                Math.pow(1 + rate, 1.0 / 12) - 1);
        Count nominal = count(portfolio.doubleValue(), monthlyWithdrawal.doubleValue(), rate / 12);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put(PORTFOLIO, portfolio);
        keyInputs.put(MONTHLY_WITHDRAWAL, monthlyWithdrawal);
        keyInputs.put(ANNUAL_RATE, annualRate);

        List<FigureInput> inputs = List.of(
                portfolioFigure == null ? FigureInput.number("Starting portfolio", portfolio, Unit.EUR)
                        : FigureInput.figure("Starting portfolio (your investments today)", portfolioFigure),
                FigureInput.number("Monthly withdrawal", monthlyWithdrawal, Unit.EUR),
                FigureInput.number("Assumed annual return", annualRate, Unit.FRACTION));

        List<Figure> outputs = new ArrayList<>();
        outputs.addAll(outputs(effective, FULL_EFFECTIVE, YEARS_EFFECTIVE, FINAL_EFFECTIVE,
                "with the return as an effective annual rate"));
        outputs.addAll(outputs(nominal, FULL_NOMINAL, YEARS_NOMINAL, FINAL_NOMINAL,
                "with the return split into twelve equal monthly rates"));
        outputs.add(new Figure(MONTHLY_WITHDRAWAL, "Monthly withdrawal", money(monthlyWithdrawal), Unit.EUR));
        outputs.add(new Figure(PORTFOLIO, "Starting portfolio", money(portfolio), Unit.EUR));
        outputs.add(new Figure(ANNUAL_RATE, "Assumed annual return", annualRate, Unit.FRACTION));

        List<String> assumptions = new ArrayList<>(List.of(
                "Withdrawals at month end after that month's return",
                "Constant return, no costs, no taxes, no inflation adjustment",
                // No digits in fixed texts: the Number Check would flag a faithful echo of them (deviation from
                // core.py).
                "Effective rate: the monthly rate compounds to the annual rate over twelve months",
                "Monthly rates: the monthly rate is the annual rate divided by twelve"));
        if (effective.perpetual() || nominal.perpetual()) {
            assumptions.add("Where the monthly return covers the withdrawal, the portfolio is never used up; this is"
                    + " shown as " + PERPETUAL + " withdrawals");
        }
        return new CoreComputation(FUNCTION, "withdrawal duration",
                "Based on the starting portfolio, monthly withdrawal and assumed return you confirmed.", keyInputs,
                inputs, outputs, assumptions, Map.of());
    }

    private record Count(int withdrawals, double finalWithdrawal, boolean perpetual) {
    }

    private static List<Figure> outputs(Count count, String fullKey, String yearsKey, String finalKey,
            String convention) {
        return List.of(
                new Figure(fullKey, "Full monthly withdrawals " + convention, BigDecimal.valueOf(count.withdrawals()),
                        Unit.COUNT),
                new Figure(yearsKey, "Duration in years " + convention,
                        BigDecimal.valueOf(count.withdrawals()).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP),
                        Unit.YEARS),
                new Figure(finalKey, "Reduced final withdrawal " + convention,
                        BigDecimal.valueOf(count.finalWithdrawal()).setScale(2, RoundingMode.HALF_UP), Unit.EUR));
    }

    /** {@code _count_withdrawals}: full withdrawals and the balance left for a reduced final one. */
    private static Count count(double balance, double withdrawal, double monthlyRate) {
        if (monthlyRate > 0 && balance * monthlyRate >= withdrawal) {
            return new Count(PERPETUAL, 0.0, true);
        }
        int n = 0;
        while (true) {
            balance *= 1 + monthlyRate;
            if (n == PERPETUAL) {
                // More withdrawals than the experiment's perpetual marker: report it as never used up.
                return new Count(PERPETUAL, 0.0, true);
            }
            if (balance >= withdrawal) {
                balance -= withdrawal;
                n++;
            } else {
                return new Count(n, balance, false);
            }
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
