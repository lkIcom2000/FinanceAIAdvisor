package de.financetool.shared;

import de.financetool.shared.domain.PersonaId;
import java.util.Objects;
import java.util.Optional;

/**
 * Who is logged in. A {@code PERSONA} account always carries its persona id; advisor and researcher accounts never do.
 */
public record AuthenticatedUser(String username, Role role, PersonaId personaId, String displayName) {

    public AuthenticatedUser {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(displayName, "displayName");
        if ((role == Role.PERSONA) != (personaId != null)) {
            throw new IllegalArgumentException("exactly the PERSONA role carries a persona id: " + username);
        }
    }

    public Optional<PersonaId> persona() {
        return Optional.ofNullable(personaId);
    }
}
