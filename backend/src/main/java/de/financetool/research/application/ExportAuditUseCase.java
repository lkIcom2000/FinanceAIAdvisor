package de.financetool.research.application;

import de.financetool.audit.AuditExport;
import de.financetool.shared.web.SecurityConfig;
import java.io.OutputStream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audit Records as JSONL for requirement verification (FR-50); research profile only. */
@Service
@Profile(SecurityConfig.RESEARCH_PROFILE)
public class ExportAuditUseCase {

    private final AuditExport export;

    ExportAuditUseCase(AuditExport export) {
        this.export = export;
    }

    /** @param runId only records of this acceptance run, or {@code null} for all */
    @Transactional(readOnly = true)
    public void export(String runId, OutputStream out) {
        export.writeJsonl(runId, out);
    }
}
