package de.financetool.personas.application;

import de.financetool.personas.PersonaDirectory;
import de.financetool.personas.PersonaSummary;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.shared.PersonaScope;
import org.springframework.stereotype.Service;

@Service
class PersonaDirectoryService implements PersonaDirectory {

    private final PersonaRepository personas;

    PersonaDirectoryService(PersonaRepository personas) {
        this.personas = personas;
    }

    @Override
    public PersonaSummary find(PersonaScope scope) {
        return personas.find(scope)
                .map(persona -> new PersonaSummary(persona.getId(), persona.getDisplayName()))
                .orElseThrow(() -> new IllegalStateException("Session persona does not exist: " + scope.id()));
    }
}
