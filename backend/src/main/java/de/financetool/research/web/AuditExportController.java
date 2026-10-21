package de.financetool.research.web;

import de.financetool.research.application.ExportAuditUseCase;
import de.financetool.shared.web.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit export for the researcher; exists only under the research profile (AD-11). */
@Tag(name = "research-audit", description = "Audit Records for requirement verification")
@RestController
@Profile(SecurityConfig.RESEARCH_PROFILE)
class AuditExportController {

    static final String JSONL = "application/x-ndjson";

    private final ExportAuditUseCase exportAudit;

    AuditExportController(ExportAuditUseCase exportAudit) {
        this.exportAudit = exportAudit;
    }

    @Operation(summary = "All Audit Records as JSONL, in insertion order",
            responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = JSONL)))
    @GetMapping(value = "/api/research/audit/export", produces = JSONL)
    void export(@Parameter(description = "Only records of this acceptance run")
            @RequestParam(required = false) String runId, HttpServletResponse response) throws IOException {
        response.setContentType(JSONL);
        response.setCharacterEncoding("UTF-8");
        exportAudit.export(runId, response.getOutputStream());
    }
}
