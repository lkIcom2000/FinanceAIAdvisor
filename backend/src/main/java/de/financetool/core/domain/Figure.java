package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * One output of a Core function before it is keyed.
 *
 * @param outputKey the output's name within its function, part of the figure key
 * @param label the figure's name in plain words
 */
@NamedInterface("results")
public record Figure(String outputKey, String label, BigDecimal value, Unit unit) {

    public Figure {
        Objects.requireNonNull(outputKey, "outputKey");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(unit, "unit");
    }
}
