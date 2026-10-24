package de.financetool.escalation;

import de.financetool.shared.PersonaScope;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Module API: the queue of cases for a human advisor (FR-41, FR-52). A turn routed to {@code ESCALATE} opens a case
 * in the caller's transaction; called outside a transaction it throws, and if the caller rolls back no case remains.
 */
public interface EscalationCases {

    enum Status {
        OPEN, CLOSED
    }

    /**
     * The snapshot a turn's case keeps: copies, so later changes to the chat do not change the case.
     *
     * @param turnId the id of the released agent message
     * @param question the user's question as written
     * @param draft the draft block routing decided on
     * @param blockReasons the route name, then {@code CHECK_ID:reason_code} per failed check
     * @param reasonClass the plain reason class the customer may see; {@code null} for none
     */
    record TurnSnapshot(UUID turnId, String question, String draft, List<String> blockReasons, String reasonClass) {

        public TurnSnapshot {
            Objects.requireNonNull(turnId, "turnId");
            Objects.requireNonNull(question, "question");
            blockReasons = List.copyOf(blockReasons);
        }
    }

    /** What the customer's chat shows of a turn's case: the plain reason class and the status, never the snapshot. */
    record TurnCase(UUID caseId, UUID turnId, String reasonClass, Status status) {
    }

    /** Opens a {@code TURN} case with status {@code OPEN}; returns its id. */
    UUID openForTurn(PersonaScope scope, TurnSnapshot snapshot);

    /** The scope's persona's turn cases by turn id. */
    Map<UUID, TurnCase> turnCases(PersonaScope scope);
}
