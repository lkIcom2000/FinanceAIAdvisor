package de.financetool.core.domain;

import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The keyed results of a Category Recommendation (Story 6.3), from one {@code CoreGateway} call.
 *
 * @param selection the {@link CategorySelection} result
 * @param details the selected category with its reasons, conflicts and selection inputs
 * @param costs the {@link CostCalculation} result, {@code null} when the topic is no investment topic
 */
@NamedInterface("results")
public record RecommendationFigures(CoreResult selection, CategorySelection.Selection details, CoreResult costs) {

    public RecommendationFigures {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(details, "details");
    }
}
