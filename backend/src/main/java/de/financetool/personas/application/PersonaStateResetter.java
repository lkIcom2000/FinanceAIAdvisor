package de.financetool.personas.application;

import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets {@code persona_state} to the persona's fixture start. Until the persona fixtures exist (Story 2.1) every
 * persona starts on the placeholder written by V3.
 */
@Service
class PersonaStateResetter implements PersonaResettable {

    static final LocalDate FIXTURE_START_DATE = LocalDate.of(2026, 10, 20);
    static final String FIXTURE_VERSION = "pre-fixtures";

    private final PersonaStateRepository personaStates;

    PersonaStateResetter(PersonaStateRepository personaStates) {
        this.personaStates = personaStates;
    }

    @Override
    public String module() {
        return "personas";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        if (personaStates.resetState(personaId.value(), FIXTURE_START_DATE, FIXTURE_VERSION) != 1) {
            throw new IllegalStateException("No persona state for " + personaId);
        }
    }
}
