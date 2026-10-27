package de.financetool.actions.adapter;

import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The table {@code mandates} (rules). Every read and write takes a {@link PersonaScope} (AD-11); only persona reset
 * deletes by persona id. Rows are never deleted otherwise.
 */
@Repository
public class MandateRepository {

    private final JdbcClient jdbc;

    MandateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One stored rule; {@code createdAt} and {@code revokedAt} are simulated dates. */
    public record Row(UUID id, RuleTemplate template, RuleBounds bounds, UUID sourceAccountId, UUID targetAccountId,
            LocalDate createdAt, LocalDate revokedAt) {
    }

    private static final String COLUMNS = """
            id, template, max_per_action, max_per_month, floor, source_account_id, target_account_id, created_at,
                revoked_at""";

    /**
     * @throws org.springframework.dao.DuplicateKeyException if the persona already has an active rule of the template
     */
    public void insert(PersonaScope scope, Row row) {
        jdbc.sql("""
                INSERT INTO mandates (id, persona_id, template, max_per_action, max_per_month, floor,
                    source_account_id, target_account_id, created_at, revoked_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(row.id(), scope.id(), row.template().name(), row.bounds().maxPerAction(),
                        row.bounds().maxPerMonth(), row.bounds().floor(), row.sourceAccountId(),
                        row.targetAccountId(), row.createdAt(), row.revokedAt())
                .update();
    }

    /** The persona's rules that are not revoked, oldest first. */
    public List<Row> active(PersonaScope scope) {
        return jdbc.sql("SELECT " + COLUMNS
                + " FROM mandates WHERE persona_id = ? AND revoked_at IS NULL ORDER BY created_at, template")
                .param(scope.id())
                .query((rs, n) -> row(rs))
                .list();
    }

    /**
     * The persona's rule of the template that is not revoked, if any (at most one, by the partial unique index),
     * locked until the transaction ends so gate runs under the same rule see each other's month sum.
     */
    public Optional<Row> activeOf(PersonaScope scope, RuleTemplate template) {
        return jdbc.sql("SELECT " + COLUMNS
                + " FROM mandates WHERE persona_id = ? AND template = ? AND revoked_at IS NULL FOR UPDATE")
                .params(scope.id(), template.name())
                .query((rs, n) -> row(rs))
                .optional();
    }

    /** The amounts of the rule's executed Actions with a simulated date in the month; 0 without any. */
    public BigDecimal executedSum(PersonaScope scope, UUID mandateId, YearMonth month) {
        return jdbc.sql("""
                SELECT coalesce(sum(amount), 0) FROM actions
                WHERE persona_id = ? AND mandate_id = ? AND state = 'EXECUTED' AND as_of BETWEEN ? AND ?""")
                .params(scope.id(), mandateId, month.atDay(1), month.atEndOfMonth())
                .query(BigDecimal.class)
                .single();
    }

    /** Whether the persona has a rule of the template that is not revoked. */
    public boolean hasActive(PersonaScope scope, RuleTemplate template) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM mandates WHERE persona_id = ? AND template = ? AND revoked_at IS NULL)""")
                .params(scope.id(), template.name())
                .query(Boolean.class)
                .single();
    }

    /** The number of the persona's rules that are not revoked. */
    public int countActive(PersonaScope scope) {
        return jdbc.sql("SELECT count(*) FROM mandates WHERE persona_id = ? AND revoked_at IS NULL")
                .param(scope.id())
                .query(Integer.class)
                .single();
    }

    /** The number of the persona's Actions under each rule; a rule without Actions is left out. */
    public Map<UUID, Integer> actionCounts(PersonaScope scope) {
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.sql("""
                SELECT mandate_id, count(*) AS n FROM actions
                WHERE persona_id = ? AND mandate_id IS NOT NULL GROUP BY mandate_id""")
                .param(scope.id())
                .query(rs -> {
                    counts.put(rs.getObject("mandate_id", UUID.class), rs.getInt("n"));
                });
        return counts;
    }

    /** Persona reset: deletes the persona's rules (after its Actions); returns the number of deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        return jdbc.sql("DELETE FROM mandates WHERE persona_id = ?").param(personaId.value()).update();
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), RuleTemplate.valueOf(rs.getString("template")),
                new RuleBounds(rs.getBigDecimal("max_per_action"), rs.getBigDecimal("max_per_month"),
                        rs.getBigDecimal("floor")),
                rs.getObject("source_account_id", UUID.class), rs.getObject("target_account_id", UUID.class),
                rs.getObject("created_at", LocalDate.class), rs.getObject("revoked_at", LocalDate.class));
    }
}
