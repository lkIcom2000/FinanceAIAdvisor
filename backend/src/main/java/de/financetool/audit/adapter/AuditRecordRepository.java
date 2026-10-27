package de.financetool.audit.adapter;

import de.financetool.shared.PersonaScope;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Insert and read only: no update or delete exists, and the database rejects both (AD-13). */
@Repository
public class AuditRecordRepository {

    /** A stored record, payload as stored JSON. */
    public record Row(long seq, UUID id, String kind, String runId, String personaId, UUID turnId, UUID actionId,
            String fixtureVersion, int schemaVersion, String payload, OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;

    AuditRecordRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String kind, String runId, String personaId, UUID turnId, UUID actionId,
            String fixtureVersion, int schemaVersion, String payload) {
        jdbc.sql("""
                INSERT INTO audit_records
                    (kind, run_id, persona_id, turn_id, action_id, fixture_version, schema_version, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)""")
                .params(kind, runId, personaId, turnId, actionId, fixtureVersion, schemaVersion, payload)
                .update();
    }

    public void insertEvidenceIfAbsent(String provId, String personaId, String origin, String originId,
            String content) {
        jdbc.sql("""
                INSERT INTO evidence_items (prov_id, persona_id, origin, origin_id, content)
                VALUES (?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (prov_id) DO NOTHING""")
                .params(provId, personaId, origin, originId, content)
                .update();
    }

    /** The contents of the evidence items with these keys that belong to the scope's persona, by key. */
    public Map<String, String> findEvidence(Collection<String> provIds, PersonaScope scope) {
        if (provIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> found = new HashMap<>();
        jdbc.sql("""
                SELECT prov_id, content::text AS content FROM evidence_items
                WHERE prov_id IN (:provIds) AND persona_id = :personaId""")
                .param("provIds", provIds)
                .param("personaId", scope.id())
                .query(rs -> {
                    found.put(rs.getString("prov_id"), rs.getString("content"));
                });
        return found;
    }

    /** The content of an evidence item, only if it belongs to the scope's persona. */
    public Optional<String> findEvidence(String provId, PersonaScope scope) {
        return jdbc.sql("""
                SELECT content::text FROM evidence_items WHERE prov_id = ? AND persona_id = ?""")
                .params(provId, scope.id())
                .query(String.class)
                .optional();
    }

    /** Records in insertion order, optionally of one run. */
    public void forEach(String runId, Consumer<Row> action) {
        jdbc.sql("""
                SELECT seq, id, kind, run_id, persona_id, turn_id, action_id, fixture_version, schema_version,
                       payload::text AS payload, created_at
                FROM audit_records
                WHERE (CAST(? AS VARCHAR) IS NULL OR run_id = ?)
                ORDER BY seq""")
                .params(runId, runId)
                // Stream through a cursor (inside the read-only transaction) instead of buffering every row.
                .withFetchSize(500)
                .query(rs -> {
                    action.accept(new Row(rs.getLong("seq"), rs.getObject("id", UUID.class), rs.getString("kind"),
                            rs.getString("run_id"), rs.getString("persona_id"), rs.getObject("turn_id", UUID.class),
                            rs.getObject("action_id", UUID.class), rs.getString("fixture_version"),
                            rs.getInt("schema_version"), rs.getString("payload"),
                            rs.getObject("created_at", OffsetDateTime.class)));
                });
    }
}
