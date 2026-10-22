package de.financetool.personas.application;

import de.financetool.personas.PersonaDirectory;
import de.financetool.personas.PersonaSummary;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaScope;
import org.springframework.stereotype.Service;

@Service
class PersonaDirectoryService implements PersonaDirectory {

    private final PersonaRepository personas;
    private final PersonaStateRepository personaStates;

    PersonaDirectoryService(PersonaRepository personas, PersonaStateRepository personaStates) {
        this.personas = personas;
        this.personaStates = personaStates;
    }

    @Override
    public PersonaSummary find(PersonaScope scope) {
        return personas.find(scope)
                .map(persona -> new PersonaSummary(persona.getId(), persona.getDisplayName()))
                .orElseThrow(() -> new IllegalStateException("Session persona does not exist: " + scope.id()));
    }

    @Override
    public String fixtureVersion(PersonaScope scope) {
        return personaStates.find(scope)
                .map(state -> state.getFixtureVersion())
                .orElseThrow(() -> new IllegalStateException("No persona state for " + scope.id()));
    }
}
