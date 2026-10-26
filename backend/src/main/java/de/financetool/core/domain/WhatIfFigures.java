package de.financetool.core.domain;

import java.util.List;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The keyed what-if projection (Story 7.1) with its yearly points for the range chart, from one {@code CoreGateway}
 * call.
 *
 * @param result outputs of {@link WhatIfProjection}
 */
@NamedInterface("results")
public record WhatIfFigures(CoreResult result, List<MonteCarloProjection.Point> points, List<String> assumptions) {

    public WhatIfFigures {
        Objects.requireNonNull(result, "result");
        points = List.copyOf(points);
        assumptions = List.copyOf(assumptions);
    }
}
