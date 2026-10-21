package de.financetool.research.web;

import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.research.application.SetSimulatedDateUseCase;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.web.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Persona reset and simulated date for reproducible sessions (FR-3). Research profile only; the persona comes from
 * the path because the researcher acts on any persona (AD-11).
 */
@Tag(name = "research-personas", description = "Reset personas and set their simulated date")
@RestController
@Profile(SecurityConfig.RESEARCH_PROFILE)
class PersonaResearchController {

    record SimulatedDateRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-06") @NotNull LocalDate date) {
    }

    private final ResetPersonaUseCase resetPersona;
    private final SetSimulatedDateUseCase setSimulatedDate;

    PersonaResearchController(ResetPersonaUseCase resetPersona, SetSimulatedDateUseCase setSimulatedDate) {
        this.resetPersona = resetPersona;
        this.setSimulatedDate = setSimulatedDate;
    }

    @Operation(summary = "Reset the persona to its fixtures and its start date")
    @PostMapping("/api/research/personas/{personaId}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@PathVariable String personaId) {
        if (!resetPersona.reset(parse(personaId))) {
            throw unknown(personaId);
        }
    }

    @Operation(summary = "Set the persona's simulated date")
    @PostMapping("/api/research/personas/{personaId}/simulated-date")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void simulatedDate(@PathVariable String personaId, @Valid @RequestBody SimulatedDateRequest request) {
        if (!setSimulatedDate.set(parse(personaId), request.date())) {
            throw unknown(personaId);
        }
    }

    private static PersonaId parse(String personaId) {
        try {
            return new PersonaId(personaId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static ResponseStatusException unknown(String personaId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No persona " + personaId);
    }
}
