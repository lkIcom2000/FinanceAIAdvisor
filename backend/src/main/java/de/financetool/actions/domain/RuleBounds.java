package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The limits a persona sets for a rule (FR-36): the most one transfer and one calendar month may move, and the source
 * account's minimum balance after a transfer. Checked by {@link RuleTexts#problem}.
 */
@NamedInterface("types")
public record RuleBounds(BigDecimal maxPerAction, BigDecimal maxPerMonth, BigDecimal floor) {

    public RuleBounds {
        Objects.requireNonNull(maxPerAction, "maxPerAction");
        Objects.requireNonNull(maxPerMonth, "maxPerMonth");
        Objects.requireNonNull(floor, "floor");
    }
}
