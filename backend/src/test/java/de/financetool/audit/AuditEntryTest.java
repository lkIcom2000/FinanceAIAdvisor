package de.financetool.audit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** A record refers to a turn or an Action, never both; responses need the turn, Actions the Action. */
class AuditEntryTest {

    static final UUID ID = UUID.randomUUID();

    @Test
    void bothIdsAreRejected() {
        assertThatThrownBy(() -> new AuditEntry(AuditKind.NOTIFICATION, ID, ID, "f", "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void responsesNeedATurnAndActionsAnAction() {
        assertThatThrownBy(() -> new AuditEntry(AuditKind.RESPONSE, null, null, "f", "body"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEntry(AuditKind.ACTION, null, null, "f", "body"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEntry(AuditKind.RESPONSE, null, ID, "f", "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notificationsAndExtractionsNeedNoId() {
        assertThatCode(() -> new AuditEntry(AuditKind.NOTIFICATION, null, null, "f", "body")).doesNotThrowAnyException();
        assertThatCode(() -> new AuditEntry(AuditKind.DOCUMENT_EXTRACTION, null, null, "f", "body"))
                .doesNotThrowAnyException();
    }
}
