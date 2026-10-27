package de.financetool.monitoring.adapter;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The table {@code triggers}. Every read and write takes a {@link PersonaScope} (AD-11); only persona reset deletes by
 * persona id.
 */
@Repository
public class TriggerRepository {

    private final JdbcClient jdbc;

    TriggerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One stored Trigger; its key is {@code (persona, type, subjectId, period)}. */
    public record Row(UUID id, String type, String subjectId, String period, LocalDate asOf) {
    }

    /** Stores the Trigger unless its key exists; returns whether it was stored. */
    public boolean insertIfAbsent(PersonaScope scope, Row row) {
        return jdbc.sql("""
                INSERT INTO triggers (id, persona_id, type, subject_id, period, as_of)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (persona_id, type, subject_id, period) DO NOTHING""")
                .params(row.id(), scope.id(), row.type(), row.subjectId(), row.period(), row.asOf())
                .update() == 1;
    }

    /** The persona's Triggers, oldest first. */
    public List<Row> findAll(PersonaScope scope) {
        return jdbc.sql("""
                SELECT id, type, subject_id, period, as_of FROM triggers
                WHERE persona_id = ? ORDER BY created_at, id""")
                .param(scope.id())
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getString("subject_id"), rs.getString("period"), rs.getObject("as_of", LocalDate.class)))
                .list();
    }

    /** Persona reset: deletes the persona's Triggers; returns the number of deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM triggers WHERE persona_id = ?").param(personaId.value()).update();
    }
}
