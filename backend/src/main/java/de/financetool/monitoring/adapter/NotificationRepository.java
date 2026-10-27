package de.financetool.monitoring.adapter;

import de.financetool.monitoring.domain.NotificationOutcome;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The table {@code notifications}. Every read and write takes a {@link PersonaScope} (AD-11); only persona reset
 * deletes by persona id.
 */
@Repository
public class NotificationRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One stored Notification. {@code actionId} is set exactly for outcome {@code ACTION}. */
    public record Row(UUID id, UUID triggerId, UUID actionId, NotificationOutcome outcome, String title, String body,
            List<String> citedProvIds, LocalDate asOf) {
    }

    public void insert(PersonaScope scope, Row row) {
        jdbc.sql("""
                INSERT INTO notifications (id, persona_id, trigger_id, action_id, outcome, title, body,
                    cited_prov_ids, as_of)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)""")
                .params(row.id(), scope.id(), row.triggerId(), row.actionId(), row.outcome().name(), row.title(),
                        row.body(), JSON.writeValueAsString(row.citedProvIds()), row.asOf())
                .update();
    }

    /** The persona's Notifications, oldest first. */
    public List<Row> findAll(PersonaScope scope) {
        return jdbc.sql("""
                SELECT id, trigger_id, action_id, outcome, title, body, cited_prov_ids::text AS cited, as_of
                FROM notifications WHERE persona_id = ? ORDER BY created_at, id""")
                .param(scope.id())
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("trigger_id", UUID.class),
                        rs.getObject("action_id", UUID.class), NotificationOutcome.valueOf(rs.getString("outcome")),
                        rs.getString("title"), rs.getString("body"),
                        List.of(JSON.readValue(rs.getString("cited"), String[].class)),
                        rs.getObject("as_of", LocalDate.class)))
                .list();
    }

    /** Persona reset: deletes the persona's Notifications; returns the number of deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM notifications WHERE persona_id = ?").param(personaId.value()).update();
    }
}
