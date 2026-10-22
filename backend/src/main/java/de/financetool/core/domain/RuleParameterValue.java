package de.financetool.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A Rule Parameter version valid at the computation date, passed into a Core function as an argument.
 *
 * @param validTo last day the version applies, or {@code null} if open-ended
 */
public record RuleParameterValue(String key, BigDecimal value, String version, LocalDate validFrom,
        LocalDate validTo) {

    public RuleParameterValue {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(validFrom, "validFrom");
    }
}
