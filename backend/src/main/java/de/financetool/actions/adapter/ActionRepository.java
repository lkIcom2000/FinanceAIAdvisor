package de.financetool.actions.adapter;

import de.financetool.actions.ActionSummary;
import de.financetool.actions.domain.ActionState;
import de.financetool.actions.domain.ActionType;
import de.financetool.actions.domain.AutonomyLevel;
import de.financetool.actions.domain.RegulatoryClass;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The table {@code actions}. Every read and write takes a {@link PersonaScope} (AD-11); only persona reset deletes by
 * persona id.
 */
@Repository
public class ActionRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    ActionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One stored Action. {@code level} is {@code null} only for an escalated Action.
     *
     * @param reason the customer reason of the first capping criterion, or {@code null}
     */
    public record Row(UUID id, ActionType type, RegulatoryClass regulatoryClass, BigDecimal amount,
            UUID sourceAccountId, UUID targetAccountId, UUID triggerId, ActionState state, AutonomyLevel level,
            List<String> cappingCriteria, LocalDate asOf, String reason) {
    }

    private static final String COLUMNS = """
            id, type, regulatory_class, amount, source_account_id, target_account_id, trigger_id, state, level,
                capping_criteria::text AS capping_criteria, as_of, reason""";

    public void insert(PersonaScope scope, Row row) {
        jdbc.sql("""
                INSERT INTO actions (id, persona_id, type, regulatory_class, amount, source_account_id,
                    target_account_id, trigger_id, state, level, capping_criteria, as_of, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)""")
                .params(row.id(), scope.id(), row.type().name(), row.regulatoryClass().id(), row.amount(),
                        row.sourceAccountId(), row.targetAccountId(), row.triggerId(), row.state().name(),
                        row.level() == null ? null : row.level().name(), JSON.writeValueAsString(row.cappingCriteria()),
                        row.asOf(), row.reason())
                .update();
    }

    /** The persona's Actions, oldest first. */
    public List<Row> findAll(PersonaScope scope) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM actions WHERE persona_id = ? ORDER BY created_at, id")
                .param(scope.id())
                .query((rs, n) -> row(rs))
                .list();
    }

    /**
     * The persona's Action with this id; locks all of the persona's Actions until the transaction ends, so two
     * confirmations of one persona never check the same account balance at once. Empty if not the persona's.
     */
    public Optional<Row> findForUpdate(PersonaScope scope, UUID id) {
        jdbc.sql("SELECT id FROM actions WHERE persona_id = ? ORDER BY id FOR UPDATE")
                .param(scope.id())
                .query()
                .listOfRows();
        return jdbc.sql("SELECT " + COLUMNS + " FROM actions WHERE id = ? AND persona_id = ? FOR UPDATE")
                .params(id, scope.id())
                .query((rs, n) -> row(rs))
                .optional();
    }

    /** The persona's Action with this id; empty if not the persona's. */
    public Optional<Row> find(PersonaScope scope, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM actions WHERE id = ? AND persona_id = ?")
                .params(id, scope.id())
                .query((rs, n) -> row(rs))
                .optional();
    }

    /** Stores the outcome of a confirmation's re-check on the same row. */
    public void updateAfterCheck(PersonaScope scope, UUID id, ActionState state, AutonomyLevel level,
            List<String> cappingCriteria, String reason) {
        int updated = jdbc.sql("""
                UPDATE actions SET state = ?, level = ?, capping_criteria = ?::jsonb, reason = ?
                WHERE id = ? AND persona_id = ?""")
                .params(state.name(), level == null ? null : level.name(), JSON.writeValueAsString(cappingCriteria),
                        reason, id, scope.id())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("no Action " + id + " for the persona");
        }
    }

    /** The states and reasons of the persona's Actions with these ids; other ids are left out. */
    public Map<UUID, ActionSummary> summaries(PersonaScope scope, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ActionSummary> summaries = new HashMap<>();
        jdbc.sql("SELECT id, state, reason FROM actions WHERE id IN (:ids) AND persona_id = :personaId")
                .param("ids", ids)
                .param("personaId", scope.id())
                .query(rs -> {
                    summaries.put(rs.getObject("id", UUID.class),
                            new ActionSummary(ActionState.valueOf(rs.getString("state")), rs.getString("reason")));
                });
        return summaries;
    }

    /** Persona reset: deletes the persona's Actions; returns the number of deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM actions WHERE persona_id = ?").param(personaId.value()).update();
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), ActionType.valueOf(rs.getString("type")),
                RegulatoryClass.valueOf(rs.getString("regulatory_class").toUpperCase(Locale.ROOT)),
                rs.getBigDecimal("amount"), rs.getObject("source_account_id", UUID.class),
                rs.getObject("target_account_id", UUID.class), rs.getObject("trigger_id", UUID.class),
                ActionState.valueOf(rs.getString("state")),
                rs.getString("level") == null ? null : AutonomyLevel.valueOf(rs.getString("level")),
                List.of(JSON.readValue(rs.getString("capping_criteria"), String[].class)),
                rs.getObject("as_of", LocalDate.class), rs.getString("reason"));
    }
}
