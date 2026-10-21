package de.financetool.audit;

import de.financetool.shared.domain.PersonaId;
import java.util.Objects;

/**
 * Module API: one evidence item keyed by its figure key (AD-2).
 *
 * @param originId the turn, run or Document the item came from, if any
 * @param content the item itself (later a {@code CoreResult}), serialised by {@code AuditPayloadMapper}
 */
public record EvidenceItem(String provId, PersonaId personaId, EvidenceOrigin origin, String originId,
        Object content) {

    public EvidenceItem {
        Objects.requireNonNull(provId, "provId");
        Objects.requireNonNull(personaId, "personaId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(content, "content");
        if (!provId.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("provId must be 16 lower-case hex characters: " + provId);
        }
    }
}
