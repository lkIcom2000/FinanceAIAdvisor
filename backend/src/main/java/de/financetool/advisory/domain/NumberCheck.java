package de.financetool.advisory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Number Check (FR-12; {@code matches} and {@code check} of {@code experiments/finlab/numbercheck.py}):
 * every number {@link NumberParser} finds in a text must match a sourced value of the {@link NumberLedger}.
 *
 * <p>Deviation: the experiment rounds a percentage's fraction to the decimals shown for the percentage, so "5%"
 * (0.05, rounded to 0) matches any sourced value below 0.5; the port rounds the fraction to two more decimals. The
 * experiment also tries every sourced value times 100, so a Rule Parameter of 6 sources "600 EUR"; the port takes
 * the hundredfold only of fractions (absolute value below 1), the values a text can show as a percentage.
 */
public final class NumberCheck {

    static final BigDecimal APPROXIMATION = new BigDecimal("0.05");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private NumberCheck() {
    }

    /**
     * Whether {@code found} matches a sourced value: the number or its absolute value, a percentage also as a
     * fraction, against each value, its absolute value and, for a fraction, both times 100 (shown as a percentage).
     * Approximate numbers ("about", "roughly", ...) may deviate by 5 %; others must equal the value after rounding
     * both to the decimals shown (half-even, as Python's {@code quantize}); a percentage's fraction has two decimals
     * more than shown.
     */
    public static boolean matches(NumberParser.Found found, List<BigDecimal> sourced) {
        Set<Candidate> candidates = new LinkedHashSet<>(List.of(new Candidate(found.value(), found.decimals()),
                new Candidate(found.value().abs(), found.decimals())));
        if (found.percent()) {
            candidates.add(new Candidate(found.value().movePointLeft(2), found.decimals() + 2));
            candidates.add(new Candidate(found.value().abs().movePointLeft(2), found.decimals() + 2));
        }
        for (Candidate candidate : candidates) {
            BigDecimal c = candidate.value();
            for (BigDecimal s : sourced) {
                List<BigDecimal> variants = s.abs().compareTo(BigDecimal.ONE) < 0
                        ? List.of(s, s.multiply(HUNDRED), s.abs(), s.abs().multiply(HUNDRED))
                        : List.of(s, s.abs());
                for (BigDecimal sv : variants) {
                    if (found.approximate()) {
                        if (sv.signum() != 0 && c.subtract(sv).abs().compareTo(sv.abs().multiply(APPROXIMATION)) <= 0) {
                            return true;
                        }
                        if (sv.signum() == 0 && c.signum() == 0) {
                            return true;
                        }
                    } else if (sv.setScale(candidate.decimals(), RoundingMode.HALF_EVEN)
                            .compareTo(c.setScale(candidate.decimals(), RoundingMode.HALF_EVEN)) == 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A value the found number may stand for, with the decimals it is compared at. */
    private record Candidate(BigDecimal value, int decimals) {
    }

    /**
     * The {@code NUMBER_MATCH} result of {@code text}: applicable on every text, failed with {@code number_mismatch}
     * if any number does not match; details are the mismatches (text, value, approximate, ok) and how many numbers were
     * checked.
     */
    public static CheckResult check(String text, List<BigDecimal> sourced) {
        List<NumberParser.Found> found = NumberParser.parse(text);
        List<Map<String, Object>> mismatches = new ArrayList<>();
        for (NumberParser.Found f : found) {
            if (!matches(f, sourced)) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("text", f.text());
                record.put("value", f.value().toPlainString());
                record.put("approximate", f.approximate());
                record.put("ok", false);
                mismatches.add(record);
            }
        }
        boolean passed = mismatches.isEmpty();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mismatches", mismatches);
        details.put("checked", found.size());
        return new CheckResult(CheckId.NUMBER_MATCH, true, passed, passed ? "" : "number_mismatch", details);
    }
}
