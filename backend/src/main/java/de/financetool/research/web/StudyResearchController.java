package de.financetool.research.web;

import de.financetool.monitoring.Monitoring;
import de.financetool.research.application.PrepareStudyUseCase;
import de.financetool.shared.web.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Prepares a persona for a study session from a named preset (FR-31). Research profile only. */
@Tag(name = "research-study", description = "Prepare personas for study sessions")
@RestController
@Profile(SecurityConfig.RESEARCH_PROFILE)
class StudyResearchController {

    /** What the participant will see; {@code summary} reads like "1 notification (1 Recommendation), 0 rules active". */
    record PrepareStudyResponse(String presetId, String title, String personaId, LocalDate simulatedDate,
            List<Monitoring.RunNotification> notifications, int activeRules, String summary) {
    }

    private static final Logger log = LoggerFactory.getLogger(StudyResearchController.class);

    private final PrepareStudyUseCase prepareStudy;

    StudyResearchController(PrepareStudyUseCase prepareStudy) {
        this.prepareStudy = prepareStudy;
    }

    @Operation(summary = "Reset the preset's persona, set its date, run setup and monitoring")
    @PostMapping("/api/research/study/prepare/{presetId}")
    PrepareStudyResponse prepare(@PathVariable String presetId, Principal researcher) {
        PrepareStudyUseCase.Prepared prepared = prepareStudy.prepare(presetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No study preset " + presetId));
        // Researcher actions are logged, not audited; the run's Notifications and Actions are audited.
        log.atInfo().setMessage("Study prepared").addKeyValue("presetId", presetId)
                .addKeyValue("personaId", prepared.personaId()).addKeyValue("summary", prepared.summary())
                .addKeyValue("researcher", researcher.getName()).log();
        return new PrepareStudyResponse(prepared.presetId(), prepared.title(), prepared.personaId(),
                prepared.simulatedDate(), prepared.notifications(), prepared.activeRules(), prepared.summary());
    }
}
