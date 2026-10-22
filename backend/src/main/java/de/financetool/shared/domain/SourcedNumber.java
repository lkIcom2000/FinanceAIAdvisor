package de.financetool.shared.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A figure produced by the Deterministic Core, with the figure key ({@code provId}) under which its provenance is
 * stored (AD-2). Every figure shown to a customer is one of these.
 *
 * @param provId 16 lower-case hex characters
 * @param label the figure's name in plain words
 */
public record SourcedNumber(String provId, BigDecimal value, Unit unit, String label) {

    public SourcedNumber {
        Objects.requireNonNull(provId, "provId");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(label, "label");
        if (!provId.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("provId must be 16 lower-case hex characters: " + provId);
        }
    }
}
