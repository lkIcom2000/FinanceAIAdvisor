package de.financetool.personas.application;

import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets {@code persona_state} to the persona's fixture: its start date ({@code simulatedDate}) and its content hash
 * as fixture version. Replaces the placeholder V3 wrote (retro item A3).
 */
@Service
class PersonaStateResetter implements PersonaResettable {

    private final PersonaStateRepository personaStates;
    private final PersonaFixtureCatalog fixtures;

    PersonaStateResetter(PersonaStateRepository personaStates, PersonaFixtureCatalog fixtures) {
        this.personaStates = personaStates;
        this.fixtures = fixtures;
    }

    @Override
    public String module() {
        return "personas";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        if (personaStates.resetState(personaId.value(), fixtures.fixture(personaId).simulatedDate(),
                fixtures.version(personaId)) != 1) {
            throw new IllegalStateException("No persona state for " + personaId);
        }
    }
}
