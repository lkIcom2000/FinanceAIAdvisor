package de.financetool.knowledge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Module API: one version of a Rule Parameter (PRD FR-22) from {@code data/rules/rule_parameters.yaml}.
 *
 * @param unit EUR, FRACTION, MONTHS, YEARS or COUNT
 * @param validTo last day this version applies, or {@code null} if open-ended
 */
public record RuleParameter(String key, BigDecimal value, String unit, LocalDate validFrom, LocalDate validTo,
        String source, String version) {

    public RuleParameter {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(version, "version");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException(key + " v" + version + ": valid_to is before valid_from");
        }
    }

    public boolean isValidAt(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
