package de.financetool.shared;

import de.financetool.shared.domain.PersonaId;
import java.util.Objects;

/**
 * Proof that a request acts for exactly one persona, derived from the authenticated principal (AD-11). Every
 * persona-scoped repository method takes one. Only {@code shared.web.PersonaScopeResolver} creates instances in
 * production code (enforced by an architecture test); controllers receive it as a method parameter.
 */
public record PersonaScope(PersonaId personaId) {

    public PersonaScope {
        Objects.requireNonNull(personaId, "personaId");
    }

    public String id() {
        return personaId.value();
    }
}
