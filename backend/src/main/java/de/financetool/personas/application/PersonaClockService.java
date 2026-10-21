package de.financetool.personas.application;

import de.financetool.personas.PersonaClock;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The researcher's persona operations, inside the calling use case's transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class PersonaClockService implements PersonaClock {

    private final PersonaStateRepository personaStates;

    PersonaClockService(PersonaStateRepository personaStates) {
        this.personaStates = personaStates;
    }

    @Override
    public boolean exists(PersonaId personaId) {
        return personaStates.existsByPersonaId(personaId.value());
    }

    @Override
    public void setSimulatedDate(PersonaId personaId, LocalDate date) {
        if (personaStates.updateSimulatedDate(personaId.value(), date) != 1) {
            throw new IllegalStateException("No persona state for " + personaId);
        }
    }
}
