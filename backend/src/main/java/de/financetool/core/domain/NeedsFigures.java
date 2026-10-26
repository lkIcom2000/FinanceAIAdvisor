package de.financetool.core.domain;

import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The keyed result of a demands-and-needs statement (Story 6.4), from one {@code CoreGateway} call.
 *
 * @param result the {@link DemandsAndNeeds} result (contract counts and Duplicate Coverage)
 * @param statement the areas with need, cover held and what to check, and the Needs Profile fields used
 */
@NamedInterface("results")
public record NeedsFigures(CoreResult result, DemandsAndNeeds.Statement statement) {

    public NeedsFigures {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(statement, "statement");
    }
}
