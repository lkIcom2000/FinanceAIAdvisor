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

    /**
     * One stored Notification. {@code actionId} is set exactly for outcome {@code ACTION}.
     *
     * @param question the prepared chat question, or {@code null}
     */
    public record Row(UUID id, UUID triggerId, UUID actionId, NotificationOutcome outcome, String title, String body,
            List<String> citedProvIds, LocalDate asOf, String question) {
    }

    /** A stored Notification with its read state. */
    public record Listed(Row row, boolean read) {
    }

    public void insert(PersonaScope scope, Row row) {
        jdbc.sql("""
                INSERT INTO notifications (id, persona_id, trigger_id, action_id, outcome, title, body,
                    cited_prov_ids, as_of, question)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)""")
                .params(row.id(), scope.id(), row.triggerId(), row.actionId(), row.outcome().name(), row.title(),
                        row.body(), JSON.writeValueAsString(row.citedProvIds()), row.asOf(), row.question())
                .update();
    }

    /** The persona's Notifications, oldest first. */
    public List<Row> findAll(PersonaScope scope) {
        return findListed(scope, "created_at, id").stream().map(Listed::row).toList();
    }

    /** The persona's Notifications with their read state, newest first. */
    public List<Listed> findNewestFirst(PersonaScope scope) {
        return findListed(scope, "created_at DESC, id");
    }

    private List<Listed> findListed(PersonaScope scope, String order) {
        return jdbc.sql("""
                SELECT id, trigger_id, action_id, outcome, title, body, cited_prov_ids::text AS cited, as_of,
                    question, read_at IS NOT NULL AS read
                FROM notifications WHERE persona_id = ? ORDER BY\s""" + order)
                .param(scope.id())
                .query((rs, n) -> new Listed(new Row(rs.getObject("id", UUID.class),
                        rs.getObject("trigger_id", UUID.class), rs.getObject("action_id", UUID.class),
                        NotificationOutcome.valueOf(rs.getString("outcome")), rs.getString("title"),
                        rs.getString("body"), List.of(JSON.readValue(rs.getString("cited"), String[].class)),
                        rs.getObject("as_of", LocalDate.class), rs.getString("question")), rs.getBoolean("read")))
                .list();
    }

    /**
     * Marks the persona's Notification read; an already read one keeps its first read time.
     *
     * @return {@code false} if no Notification with this id belongs to the persona
     */
    public boolean markRead(PersonaScope scope, UUID id) {
        return jdbc.sql("""
                UPDATE notifications SET read_at = COALESCE(read_at, clock_timestamp())
                WHERE id = ? AND persona_id = ?""")
                .params(id, scope.id())
                .update() == 1;
    }

    /** Persona reset: deletes the persona's Notifications; returns the number of deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM notifications WHERE persona_id = ?").param(personaId.value()).update();
    }
}
