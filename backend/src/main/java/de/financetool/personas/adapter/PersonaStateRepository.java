package de.financetool.personas.adapter;

import de.financetool.shared.PersonaScope;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** The persona's simulated clock and fixture version; reads take a {@link PersonaScope} (AD-11). */
public interface PersonaStateRepository extends Repository<PersonaStateEntity, String> {

    Optional<PersonaStateEntity> findByPersonaId(String personaId);

    default Optional<PersonaStateEntity> find(PersonaScope scope) {
        return findByPersonaId(scope.id());
    }
}
