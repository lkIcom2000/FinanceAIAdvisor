package de.financetool.personas.adapter;

import de.financetool.shared.PersonaScope;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Persona-scoped reads take a {@link PersonaScope}, never a raw id (AD-11). */
public interface PersonaRepository extends Repository<PersonaEntity, String> {

    Optional<PersonaEntity> findById(String id);

    default Optional<PersonaEntity> find(PersonaScope scope) {
        return findById(scope.id());
    }
}
