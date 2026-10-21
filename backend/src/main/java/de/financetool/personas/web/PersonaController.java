package de.financetool.personas.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.financetool.personas.application.ShowPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.web.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Persona endpoints: the persona comes only from the session, never from the request. */
@Tag(name = "persona", description = "The persona of the session")
@RestController
class PersonaController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PersonaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
            @Schema(description = "The persona's simulated date; present only under the research profile")
            LocalDate today) {
    }

    private final ShowPersonaUseCase showPersona;
    private final boolean research;

    PersonaController(ShowPersonaUseCase showPersona, Environment environment) {
        this.showPersona = showPersona;
        this.research = environment.acceptsProfiles(Profiles.of(SecurityConfig.RESEARCH_PROFILE));
    }

    @Operation(summary = "The persona of the current session")
    @GetMapping("/api/persona")
    PersonaResponse current(@Parameter(hidden = true) PersonaScope scope) {
        ShowPersonaUseCase.PersonaView view = showPersona.show(scope);
        return new PersonaResponse(view.persona().id(), view.persona().displayName(), research ? view.asOf() : null);
    }
}
