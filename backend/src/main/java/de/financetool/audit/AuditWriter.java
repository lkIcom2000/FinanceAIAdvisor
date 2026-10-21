package de.financetool.audit;

import de.financetool.shared.RequestContext;

/**
 * Module API: writes an append-only Audit Record in the caller's transaction (AD-7, AD-13). Called outside a
 * transaction it throws; if it throws, the caller's transaction rolls back and nothing is released.
 */
public interface AuditWriter {

    /** Version of the record and payload format; raised when the format changes. */
    int SCHEMA_VERSION = 1;

    void write(RequestContext context, AuditEntry entry);
}
