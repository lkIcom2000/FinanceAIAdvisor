package de.financetool.actions.adapter;

import de.financetool.actions.domain.ActionState;
import de.financetool.actions.domain.ActionType;
import de.financetool.actions.domain.AutonomyLevel;
import de.financetool.actions.domain.RegulatoryClass;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    /** One stored Action. {@code level} is {@code null} only for an escalated Action. */
    public record Row(UUID id, ActionType type, RegulatoryClass regulatoryClass, BigDecimal amount,
            UUID sourceAccountId, UUID targetAccountId, UUID triggerId, ActionState state, AutonomyLevel level,
            List<String> cappingCriteria, LocalDate asOf) {
    }

    public void insert(PersonaScope scope, Row row) {
        jdbc.sql("""
                INSERT INTO actions (id, persona_id, type, regulatory_class, amount, source_account_id,
                    target_account_id, trigger_id, state, level, capping_criteria, as_of)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)""")
                .params(row.id(), scope.id(), row.type().name(), row.regulatoryClass().id(), row.amount(),
                        row.sourceAccountId(), row.targetAccountId(), row.triggerId(), row.state().name(),
                        row.level() == null ? null : row.level().name(), JSON.writeValueAsString(row.cappingCriteria()),
                        row.asOf())
                .update();
    }

    /** The persona's Actions, oldest first. */
    public List<Row> findAll(PersonaScope scope) {
        return jdbc.sql("""
                SELECT id, type, regulatory_class, amount, source_account_id, target_account_id, trigger_id, state,
                    level, capping_criteria::text AS capping_criteria, as_of
                FROM actions WHERE persona_id = ? ORDER BY created_at, id""")
                .param(scope.id())
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), ActionType.valueOf(rs.getString("type")),
                        RegulatoryClass.valueOf(rs.getString("regulatory_class").toUpperCase(java.util.Locale.ROOT)),
                        rs.getBigDecimal("amount"), rs.getObject("source_account_id", UUID.class),
                        rs.getObject("target_account_id", UUID.class), rs.getObject("trigger_id", UUID.class),
                        ActionState.valueOf(rs.getString("state")),
                        rs.getString("level") == null ? null : AutonomyLevel.valueOf(rs.getString("level")),
                        List.of(JSON.readValue(rs.getString("capping_criteria"), String[].class)),
                        rs.getObject("as_of", LocalDate.class)))
                .list();
    }

    /** Persona reset: deletes the persona's Actions; returns the number of deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM actions WHERE persona_id = ?").param(personaId.value()).update();
    }
}
