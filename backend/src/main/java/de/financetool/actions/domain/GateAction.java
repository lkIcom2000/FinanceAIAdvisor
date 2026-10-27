package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The Action as the Autonomy Gate sees it.
 *
 * @param amount in EUR; {@code null} counts as 0
 */
public record GateAction(ActionType type, RegulatoryClass regulatoryClass, BigDecimal amount, boolean reversible) {

    public GateAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(regulatoryClass, "regulatoryClass");
    }

    /** The amount, 0 if none. */
    public BigDecimal amountOrZero() {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
