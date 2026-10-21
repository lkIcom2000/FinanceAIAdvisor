package de.financetool.personas.web;

import de.financetool.personas.PersonaDirectory;
import de.financetool.personas.PersonaSummary;
import de.financetool.shared.PersonaScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Persona endpoints: the persona comes only from the session, never from the request. */
@Tag(name = "persona", description = "The persona of the session")
@RestController
class PersonaController {

    private final PersonaDirectory directory;

    PersonaController(PersonaDirectory directory) {
        this.directory = directory;
    }

    @Operation(summary = "The persona of the current session")
    @GetMapping("/api/persona")
    PersonaSummary current(@Parameter(hidden = true) PersonaScope scope) {
        return directory.find(scope);
    }
}
