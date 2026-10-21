package de.financetool.audit;

import java.io.OutputStream;

/** Module API: Audit Records as JSONL for requirement verification (FR-50). Runs in the caller's transaction. */
public interface AuditExport {

    /**
     * Writes one JSON object per line, in insertion order.
     *
     * @param runId only records of this acceptance run, or {@code null} for all records
     */
    void writeJsonl(String runId, OutputStream out);
}
