package de.financetool.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Data Confidence: whether the transaction data is complete enough to act on. It is high when at least
 * {@code minCoverage} of the bookings in the last {@value #WINDOW_DAYS} days have a category and no gap between two
 * booking days (or between the last booking and {@code asOf}) is longer than {@code maxGapDays}. Pure; the thresholds
 * come from the configuration ({@code config/autonomy.yaml}).
 */
public final class DataConfidence {

    public static final String CHECK_ID = "data-confidence";
    static final int WINDOW_DAYS = 90;

    /**
     * @param coverage share of bookings in the window with a category (0 without bookings)
     * @param longestGapDays the longest gap in days
     */
    public record Result(BigDecimal coverage, long longestGapDays, int bookings, boolean high,
            BigDecimal minCoverage, int maxGapDays) {

        /** The result as a check on a figure computed from this data. */
        public CheckResult toCheck() {
            String detail = Wording.percent(coverage.setScale(3, RoundingMode.DOWN)) + " of your bookings in the last "
                    + WINDOW_DAYS + " days have a category, and the longest gap without bookings is "
                    + longestGapDays + (longestGapDays == 1 ? " day" : " days") + ".";
            return high
                    ? new CheckResult(CHECK_ID, true, "Your account data is complete enough: " + detail)
                    : new CheckResult(CHECK_ID, false, "Your account data may be incomplete: " + detail
                            + " Fine needs at least " + Wording.percent(minCoverage) + " and no gap longer than "
                            + maxGapDays + " days.");
        }
    }

    private DataConfidence() {
    }

    public static Result of(List<LedgerEntry> transactions, LocalDate asOf, BigDecimal minCoverage, int maxGapDays) {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(minCoverage, "minCoverage");
        LocalDate windowStart = asOf.minusDays(WINDOW_DAYS - 1);
        List<LedgerEntry> window = transactions.stream()
                .filter(t -> !t.date().isBefore(windowStart) && !t.date().isAfter(asOf))
                .toList();
        long categorised = window.stream().filter(t -> t.category() != null && !t.category().isBlank()).count();
        BigDecimal coverage = window.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(categorised).divide(BigDecimal.valueOf(window.size()), 4, RoundingMode.HALF_UP);

        List<LocalDate> days = window.stream().map(LedgerEntry::date).distinct().sorted().toList();
        long longestGap = days.isEmpty() ? WINDOW_DAYS : 0;
        for (int i = 1; i < days.size(); i++) {
            longestGap = Math.max(longestGap, ChronoUnit.DAYS.between(days.get(i - 1), days.get(i)));
        }
        if (!days.isEmpty()) {
            longestGap = Math.max(longestGap, ChronoUnit.DAYS.between(days.getLast(), asOf));
        }
        boolean high = !window.isEmpty() && coverage.compareTo(minCoverage) >= 0 && longestGap <= maxGapDays;
        return new Result(coverage, longestGap, window.size(), high, minCoverage, maxGapDays);
    }
}
