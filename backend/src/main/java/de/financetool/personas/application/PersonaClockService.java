package de.financetool.personas.application;

import de.financetool.personas.PersonaClock;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The researcher's persona operations, inside the calling use case's transaction. */
@Service
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
    public Map<PersonaId, String> fixtureVersions() {
        Map<PersonaId, String> versions = new LinkedHashMap<>();
        personaStates.findAllByOrderByPersonaIdAsc()
                .forEach(state -> versions.put(new PersonaId(state.getPersonaId()), state.getFixtureVersion()));
        return versions;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setSimulatedDate(PersonaId personaId, LocalDate date) {
        if (personaStates.updateSimulatedDate(personaId.value(), date) != 1) {
            throw new IllegalStateException("No persona state for " + personaId);
        }
    }
}
