package de.financetool.audit;

import java.util.List;

/** Module API: the append-only evidence store (AD-2). Runs in the caller's transaction. */
public interface EvidenceStore {

    /** Inserts each item whose {@code provId} is new; an existing {@code provId} is left unchanged. */
    void putIfAbsent(List<EvidenceItem> items);
}
