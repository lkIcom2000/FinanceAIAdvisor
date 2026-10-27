package de.financetool.escalation.adapter;

import de.financetool.escalation.EscalationCases;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The table {@code escalation_cases}. Every read and write takes a {@link PersonaScope} (AD-11); only persona reset
 * deletes by persona id.
 */
@Repository
public class EscalationCaseRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    EscalationCaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertTurnCase(PersonaScope scope, UUID id, EscalationCases.TurnSnapshot snapshot) {
        jdbc.sql("""
                INSERT INTO escalation_cases (id, persona_id, origin, turn_id, question, draft, block_reasons,
                    reason_class, status)
                VALUES (?, ?, 'TURN', ?, ?, ?, ?::jsonb, ?, 'OPEN')""")
                .params(id, scope.id(), snapshot.turnId(), snapshot.question(), snapshot.draft(),
                        JSON.writeValueAsString(snapshot.blockReasons()), snapshot.reasonClass())
                .update();
    }

    public void insertTriggerCase(PersonaScope scope, UUID id, EscalationCases.TriggerSnapshot snapshot) {
        List<String> reasons = new ArrayList<>();
        reasons.add("TRIGGER:" + snapshot.triggerId());
        reasons.addAll(snapshot.reasons());
        jdbc.sql("""
                INSERT INTO escalation_cases (id, persona_id, origin, turn_id, question, block_reasons, status)
                VALUES (?, ?, 'TRIGGER', NULL, ?, ?::jsonb, 'OPEN')""")
                .params(id, scope.id(), snapshot.summary(), JSON.writeValueAsString(reasons))
                .update();
    }

    /** The persona's turn cases by turn id, oldest first. */
    public Map<UUID, EscalationCases.TurnCase> turnCases(PersonaScope scope) {
        Map<UUID, EscalationCases.TurnCase> cases = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT id, turn_id, reason_class, status FROM escalation_cases
                WHERE persona_id = ? AND origin = 'TURN' ORDER BY created_at, id""")
                .param(scope.id())
                .query((rs, row) -> new EscalationCases.TurnCase(rs.getObject("id", UUID.class),
                        rs.getObject("turn_id", UUID.class), rs.getString("reason_class"),
                        EscalationCases.Status.valueOf(rs.getString("status"))))
                .list()
                .forEach(c -> cases.put(c.turnId(), c));
        return cases;
    }

    /** Persona reset: deletes the persona's cases; returns the deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM escalation_cases WHERE persona_id = ?").param(personaId.value()).update();
    }
}
