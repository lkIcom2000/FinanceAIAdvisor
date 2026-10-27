package de.financetool.research.web;

import de.financetool.monitoring.Monitoring;
import de.financetool.research.application.RunMonitoringUseCase;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.web.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Starts a monitoring run at the persona's simulated date (FR-30). Research profile only; no scheduler exists. */
@Tag(name = "research-monitoring", description = "Run monitoring for a persona")
@RestController
@Profile(SecurityConfig.RESEARCH_PROFILE)
class MonitoringResearchController {

    /** What the run added; empty lists on a re-run at the same date. */
    record MonitoringRunResponse(List<Monitoring.RunTrigger> triggers, List<Monitoring.RunAction> actions,
            List<UUID> notificationIds) {
    }

    private static final Logger log = LoggerFactory.getLogger(MonitoringResearchController.class);

    private final RunMonitoringUseCase runMonitoring;

    MonitoringResearchController(RunMonitoringUseCase runMonitoring) {
        this.runMonitoring = runMonitoring;
    }

    @Operation(summary = "Run monitoring for the persona at its simulated date")
    @PostMapping("/api/research/personas/{personaId}/monitoring/run")
    MonitoringRunResponse run(@PathVariable String personaId, Principal researcher) {
        PersonaId id;
        try {
            id = new PersonaId(personaId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        Monitoring.Run run = runMonitoring.run(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No persona " + personaId));
        // Researcher actions are logged, not audited; the run's Notifications and Actions are audited.
        log.atInfo().setMessage("Monitoring run").addKeyValue("personaId", personaId)
                .addKeyValue("triggers", run.triggers().size()).addKeyValue("researcher", researcher.getName()).log();
        return new MonitoringRunResponse(run.triggers(), run.actions(), run.notificationIds());
    }
}
