package de.financetool.core.domain;

import java.util.Objects;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * The persona's accounts an Action can move money between; ids only, no figures.
 *
 * @param overnight {@code null} if the persona has no overnight deposit
 */
@NamedInterface("results")
public record AccountIds(UUID current, UUID overnight) {

    public AccountIds {
        Objects.requireNonNull(current, "current");
    }
}
