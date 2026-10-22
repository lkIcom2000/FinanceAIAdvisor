package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code cashFlowForecast}: the current account's daily balance from the day after {@code asOf} to the
 * same day three calendar months later, as a range. Pure.
 *
 * <p>Model (Story 2.1 reference): start from the balance at the end of {@code asOf}; per day book the recurring flows
 * due that day (day of month clamped to the month length), then variable spending V / days in month, then the
 * premiums due that day. The expected line uses V, the low line V × (1 + band), the high line V × (1 − band). Balances
 * are rounded to cents only in the output.
 *
 * <p>A forecast without assumptions or without its low and high lines fails validation (constructor).
 */
public record CashFlowForecast(LocalDate asOf, List<Point> points, List<String> assumptions,
        CoreComputation computation) {

    public static final String FUNCTION = "cashFlowForecast";
    public static final String OUTPUT_EXPECTED = "expectedEnd";
    public static final String OUTPUT_LOW = "lowEnd";
    public static final String OUTPUT_HIGH = "highEnd";
    public static final String BAND = "forecast_variable_spending_band";
    static final int HORIZON_MONTHS = 3;

    /** One forecast day; {@code low} and {@code high} are the range around {@code expected}. */
    public record Point(LocalDate date, BigDecimal low, BigDecimal expected, BigDecimal high) {

        public Point {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(expected, "expected");
        }
    }

    /** A monthly flow on the current account with the amount and day of its latest occurrence. */
    public record Recurrence(String id, String description, int day, BigDecimal amount) {

        public Recurrence {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(amount, "amount");
        }
    }

    /**
     * A contract premium debited from the current account.
     *
     * @param amount the premium, positive
     * @param nextDueDate the stored next due date, rolled forward past {@code asOf} by the forecast
     */
    public record Premium(String description, BigDecimal amount, int intervalMonths, LocalDate nextDueDate) {

        public Premium {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(nextDueDate, "nextDueDate");
            if (intervalMonths < 1) {
                throw new IllegalArgumentException("payment interval must be at least one month");
            }
        }
    }

    public CashFlowForecast {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(computation, "computation");
        points = List.copyOf(points);
        assumptions = List.copyOf(assumptions);
        if (assumptions.isEmpty()) {
            throw new IllegalArgumentException("a forecast carries its assumptions");
        }
        if (points.isEmpty() || points.stream().anyMatch(p -> p.low() == null || p.high() == null)) {
            throw new IllegalArgumentException("a forecast is a range: every day needs a low and a high balance");
        }
    }

    public LocalDate horizonEnd() {
        return points.getLast().date();
    }

    /**
     * The monthly flows seen on the current account in the last two complete months before {@code asOf}, each with
     * the amount and day of its latest occurrence up to {@code asOf}; ordered by day, then id.
     */
    public static List<Recurrence> recurrences(List<LedgerEntry> currentAccount, LocalDate asOf) {
        YearMonth last = VariableSpending.lastCompleteMonth(asOf);
        LocalDate windowStart = last.minusMonths(1).atDay(1);
        LocalDate windowEnd = last.atEndOfMonth();
        Map<String, LedgerEntry> latest = new LinkedHashMap<>();
        currentAccount.stream()
                .filter(e -> e.recurrenceId() != null && !e.date().isAfter(asOf))
                .sorted(Comparator.comparing(LedgerEntry::date))
                .forEach(e -> latest.put(e.recurrenceId(), e));
        return currentAccount.stream()
                .filter(e -> e.recurrenceId() != null && !e.date().isBefore(windowStart)
                        && !e.date().isAfter(windowEnd))
                .map(LedgerEntry::recurrenceId)
                .distinct()
                .map(latest::get)
                .map(e -> new Recurrence(e.recurrenceId(),
                        e.description() == null ? e.recurrenceId() : e.description(),
                        e.date().getDayOfMonth(), e.amount()))
                .sorted(Comparator.comparingInt(Recurrence::day).thenComparing(Recurrence::id))
                .toList();
    }

    /** The first due date after {@code asOf}: the stored date rolled forward by whole payment intervals. */
    public static LocalDate nextDueAfter(Premium premium, LocalDate asOf) {
        return dueDate(premium, firstDueIndex(premium, asOf));
    }

    private static int firstDueIndex(Premium premium, LocalDate asOf) {
        int k = 0;
        while (!dueDate(premium, k).isAfter(asOf)) {
            k++;
        }
        return k;
    }

    private static LocalDate dueDate(Premium premium, int k) {
        return premium.nextDueDate().plusMonths((long) k * premium.intervalMonths());
    }

    /**
     * @param startBalance the current account balance at the end of {@code asOf} (a Core figure)
     * @param variableMonthly the average monthly variable spending V (a Core figure)
     * @param band the Rule Parameter {@value #BAND}: the share by which variable spending may be higher or lower
     */
    public static CashFlowForecast of(SourcedNumber startBalance, SourcedNumber variableMonthly,
            List<Recurrence> recurrences, List<Premium> premiums, RuleParameterValue band, LocalDate asOf) {
        Objects.requireNonNull(startBalance, "startBalance");
        Objects.requireNonNull(variableMonthly, "variableMonthly");
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(asOf, "asOf");
        if (!BAND.equals(band.key())) {
            throw new IllegalArgumentException("expected Rule Parameter " + BAND + ", got " + band.key());
        }
        LocalDate end = asOf.plusMonths(HORIZON_MONTHS);
        BigDecimal v = variableMonthly.value();
        BigDecimal vLow = v.multiply(BigDecimal.ONE.add(band.value()));
        BigDecimal vHigh = v.multiply(BigDecimal.ONE.subtract(band.value()));

        List<LocalDate> premiumDue = new ArrayList<>();
        List<BigDecimal> premiumAmount = new ArrayList<>();
        List<Object> premiumKeys = new ArrayList<>();
        List<FigureInput> premiumInputs = new ArrayList<>();
        for (Premium premium : premiums) {
            LocalDate next = nextDueAfter(premium, asOf);
            premiumKeys.add(Map.of("description", premium.description(), "amount", premium.amount(),
                    "intervalMonths", premium.intervalMonths(), "nextDue", next));
            premiumInputs.add(FigureInput.text(premium.description(),
                    Wording.signedEur(premium.amount().negate()) + ", next due on " + Wording.date(next)));
            for (int k = firstDueIndex(premium, asOf); !dueDate(premium, k).isAfter(end); k++) {
                premiumDue.add(dueDate(premium, k));
                premiumAmount.add(premium.amount());
            }
        }

        BigDecimal low = startBalance.value();
        BigDecimal expected = startBalance.value();
        BigDecimal high = startBalance.value();
        List<Point> points = new ArrayList<>();
        for (LocalDate day = asOf.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
            int length = YearMonth.from(day).lengthOfMonth();
            for (Recurrence r : recurrences) {
                if (day.getDayOfMonth() == Math.min(r.day(), length)) {
                    low = low.add(r.amount());
                    expected = expected.add(r.amount());
                    high = high.add(r.amount());
                }
            }
            BigDecimal days = BigDecimal.valueOf(length);
            low = low.subtract(vLow.divide(days, MathContext.DECIMAL128));
            expected = expected.subtract(v.divide(days, MathContext.DECIMAL128));
            high = high.subtract(vHigh.divide(days, MathContext.DECIMAL128));
            for (int i = 0; i < premiumDue.size(); i++) {
                if (premiumDue.get(i).equals(day)) {
                    low = low.subtract(premiumAmount.get(i));
                    expected = expected.subtract(premiumAmount.get(i));
                    high = high.subtract(premiumAmount.get(i));
                }
            }
            points.add(new Point(day, money(low), money(expected), money(high)));
        }
        Point last = points.getLast();

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("startBalance", startBalance.value());
        keyInputs.put("variableMonthly", v);
        keyInputs.put("recurrences", recurrences.stream()
                .map(r -> (Object) Map.of("id", r.id(), "day", r.day(), "amount", r.amount())).toList());
        keyInputs.put("premiums", premiumKeys);
        keyInputs.put("band", band.value());
        keyInputs.put("bandVersion", band.version());
        keyInputs.put("horizonEnd", end);

        List<FigureInput> inputs = new ArrayList<>();
        inputs.add(FigureInput.figure("Current account balance on " + Wording.date(asOf), startBalance));
        inputs.add(FigureInput.figure("Average monthly variable spending", variableMonthly));
        inputs.add(FigureInput.number("Possible range of variable spending (higher or lower)", band.value(),
                Unit.FRACTION));
        for (Recurrence r : recurrences) {
            inputs.add(FigureInput.text(r.description(),
                    Wording.signedEur(r.amount()) + " on day " + r.day() + " of each month"));
        }
        inputs.addAll(premiumInputs);

        List<String> assumptions = List.of(
                "Starts from your current account balance at the end of " + Wording.date(asOf) + " and runs to "
                        + Wording.date(end) + ".",
                "Regular payments and income continue on the same day each month with their latest amount.",
                "Variable spending of " + Wording.eur(v) + " a month is spread evenly over the days of each month.",
                "Insurance and pension premiums are paid on their due dates.",
                "The possible range assumes variable spending up to " + Wording.percent(band.value())
                        + " higher or lower.");

        String endDate = Wording.date(end);
        CoreComputation computation = new CoreComputation(FUNCTION, "cash-flow forecast",
                "Based on your current account bookings up to " + Wording.date(asOf) + ".",
                keyInputs, inputs,
                List.of(new Figure(OUTPUT_EXPECTED, "Expected balance on " + endDate, last.expected(), Unit.EUR),
                        new Figure(OUTPUT_LOW, "Balance on " + endDate + " with higher spending", last.low(),
                                Unit.EUR),
                        new Figure(OUTPUT_HIGH, "Balance on " + endDate + " with lower spending", last.high(),
                                Unit.EUR)),
                assumptions,
                Map.of(band.key(), band.version()));
        return new CashFlowForecast(asOf, points, assumptions, computation);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
