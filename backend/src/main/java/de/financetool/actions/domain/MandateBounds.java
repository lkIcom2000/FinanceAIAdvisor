package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The bounds of the Mandate covering an Action.
 *
 * @param maxPerAction the largest amount one Action may move
 * @param floor the emergency fund must not fall below this after the Action
 */
public record MandateBounds(BigDecimal maxPerAction, BigDecimal floor) {

    public MandateBounds {
        Objects.requireNonNull(maxPerAction, "maxPerAction");
        Objects.requireNonNull(floor, "floor");
    }
}
