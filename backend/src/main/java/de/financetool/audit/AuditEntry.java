package de.financetool.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * Module API: one Audit Record to write. Persona, run and configuration come from the {@code RequestContext}.
 *
 * @param turnId the chat turn; required for {@link AuditKind#RESPONSE}
 * @param actionId the Action; required for {@link AuditKind#ACTION}
 * @param fixtureVersion the persona's fixture version the use case ran on
 * @param body the kind-specific payload part, serialised by {@code AuditPayloadMapper}
 */
public record AuditEntry(AuditKind kind, UUID turnId, UUID actionId, String fixtureVersion, Object body) {

    public AuditEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fixtureVersion, "fixtureVersion");
        Objects.requireNonNull(body, "body");
        if (turnId != null && actionId != null) {
            throw new IllegalArgumentException("an Audit Record refers to a turn or an Action, not both");
        }
        if (kind == AuditKind.RESPONSE && turnId == null) {
            throw new IllegalArgumentException("a RESPONSE record needs a turnId");
        }
        if (kind == AuditKind.ACTION && actionId == null) {
            throw new IllegalArgumentException("an ACTION record needs an actionId");
        }
    }
}
