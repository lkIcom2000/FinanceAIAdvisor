package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One input of a Core function as shown in "What went in": a number with unit, or a plain text, optionally with the
 * figure key of the figure it came from (for drill-down).
 *
 * @param value the number, or {@code null} for a text input
 * @param unit the number's unit, or {@code null} for a text input
 * @param text the text, or {@code null} for a number input
 * @param provId the input's own figure key, or {@code null} if it is not a Core figure
 */
public record FigureInput(String label, BigDecimal value, Unit unit, String text, String provId) {

    public FigureInput {
        Objects.requireNonNull(label, "label");
        if ((value == null) != (unit == null)) {
            throw new IllegalArgumentException("a number input needs value and unit: " + label);
        }
        if ((value == null) == (text == null)) {
            throw new IllegalArgumentException("an input is either a number or a text: " + label);
        }
    }

    public static FigureInput number(String label, BigDecimal value, Unit unit) {
        return new FigureInput(label, value, unit, null, null);
    }
}
