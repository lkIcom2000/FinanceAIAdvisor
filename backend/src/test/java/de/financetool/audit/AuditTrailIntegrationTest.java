package de.financetool.audit;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.PersonaId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** AD-13: append-only Audit Records and evidence items, written in the caller's transaction (Story 1.5). */
class AuditTrailIntegrationTest extends PostgresIntegrationTest {

    record Body(String text) {
    }

    /** A body whose serialisation fails: the audit write throws. */
    record Unserialisable(String text) {
        @Override
        public String text() {
            throw new IllegalStateException("forced audit failure");
        }
    }

    @Autowired
    AuditWriter writer;

    @Autowired
    EvidenceStore evidence;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    static String unique() {
        return UUID.randomUUID().toString();
    }

    static String provId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    RequestContext context(String runId) {
        return contexts.forPersona(new PersonaScope(new PersonaId("p-lena")), runId);
    }

    @Test
    void aRecordHoldsItsFieldsAndTheConfigurationItRanWith() {
        String runId = unique();
        UUID turnId = UUID.randomUUID();
        RequestContext context = context(runId);
        transaction.executeWithoutResult(tx -> writer.write(context,
                new AuditEntry(AuditKind.RESPONSE, turnId, null, "fixture-1", new Body("hello"))));

        Map<String, Object> row = jdbc.queryForMap("SELECT kind, run_id, persona_id, turn_id, action_id, "
                + "fixture_version, schema_version, payload::text AS payload FROM audit_records WHERE run_id = ?",
                runId);
        assertThat(row).containsEntry("kind", "RESPONSE").containsEntry("persona_id", "p-lena")
                .containsEntry("turn_id", turnId).containsEntry("fixture_version", "fixture-1")
                .containsEntry("schema_version", AuditWriter.SCHEMA_VERSION);
        assertThat(row.get("action_id")).isNull();

        Map<?, ?> payload = JsonMapper.builder().build().readValue((String) row.get("payload"), Map.class);
        Map<?, ?> config = (Map<?, ?>) payload.get("config");
        assertThat(config.get("id")).isEqualTo(context.agentConfig().id());
        assertThat(config.get("fingerprint")).isEqualTo(context.configFingerprint().value());
        assertThat(config.get("files")).isEqualTo(Map.copyOf(context.configFingerprint().fileHashes()));
        assertThat(context.configFingerprint().fileHashes()).containsKey("config/agent/reference.yaml");
        assertThat(payload.get("kbSnapshot")).isEqualTo(RequestContext.NO_KB_SNAPSHOT);
        assertThat(payload.get("body")).isEqualTo(Map.of("text", "hello"));
    }

    @Test
    void writersRefuseToRunOutsideATransaction() {
        RequestContext context = context(null);
        assertThatThrownBy(() -> writer.write(context,
                new AuditEntry(AuditKind.NOTIFICATION, null, null, "fixture-1", new Body("x"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> evidence.putIfAbsent(List.of(item(provId(), "x"))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void anEvidenceItemWithAnExistingProvIdIsANoOp() {
        String provId = provId();
        transaction.executeWithoutResult(tx -> evidence.putIfAbsent(List.of(item(provId, "first"))));
        transaction.executeWithoutResult(tx -> evidence.putIfAbsent(List.of(item(provId, "second"))));

        assertThat(jdbc.queryForList("SELECT content->>'text' FROM evidence_items WHERE prov_id = ?",
                String.class, provId)).containsExactly("first");
    }

    @Test
    void aFailingAuditWriteRollsBackEverythingTheUseCaseWrote() {
        String provId = provId();
        String runId = unique();
        RequestContext context = context(runId);

        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            evidence.putIfAbsent(List.of(item(provId, "released?")));
            writer.write(context, new AuditEntry(AuditKind.RESPONSE, UUID.randomUUID(), null, "fixture-1",
                    new Unserialisable("x")));
        })).hasRootCauseMessage("forced audit failure");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence_items WHERE prov_id = ?", Long.class, provId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_records WHERE run_id = ?", Long.class, runId))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE audit_records SET fixture_version = 'changed'",
            "DELETE FROM audit_records",
            "TRUNCATE audit_records",
            "UPDATE evidence_items SET origin_id = 'changed'",
            "DELETE FROM evidence_items",
            "TRUNCATE evidence_items"})
    void theApplicationConnectionCannotChangeAuditTables(String sql) {
        assertThatThrownBy(() -> jdbc.execute(sql))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("permission denied");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE audit_records SET fixture_version = 'changed'",
            "DELETE FROM audit_records",
            "TRUNCATE audit_records",
            "UPDATE evidence_items SET origin_id = 'changed'",
            "DELETE FROM evidence_items",
            "TRUNCATE evidence_items"})
    // The triggers reject changes on the owner connection too. The owner could still disable or drop them; the
    // application never connects as the owner (AD-13), so that is outside what these tables protect against.
    void theTriggersRejectChangesEvenOnTheOwnerConnection(String sql) throws SQLException {
        RequestContext context = context(unique());
        transaction.executeWithoutResult(tx -> {
            writer.write(context, new AuditEntry(AuditKind.NOTIFICATION, null, null, "fixture-1", new Body("x")));
            evidence.putIfAbsent(List.of(item(provId(), "x")));
        });

        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = owner.createStatement()) {
            assertThatThrownBy(() -> statement.execute(sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("audit tables are append-only");
        }
    }

    @Test
    void theApplicationKeepsFullAccessToItsOtherTables() {
        assertThat(jdbc.update("UPDATE persona_state SET fixture_version = fixture_version WHERE persona_id = 'p-tim'"))
                .isEqualTo(1);
    }

    static EvidenceItem item(String provId, String text) {
        return new EvidenceItem(provId, new PersonaId("p-lena"), EvidenceOrigin.OVERVIEW, null, new Body(text));
    }

    /** The JSONL export, only under the research profile and only for the researcher. */
    @Nested
    @ActiveProfiles("research")
    class Export extends PostgresIntegrationTest {

        @Autowired
        AuditWriter writer;

        @Autowired
        RequestContextFactory contexts;

        @Autowired
        TransactionTemplate transaction;

        @Test
        void exportsTheRecordsOfARunAsJsonlInInsertionOrder() throws Exception {
            String runId = unique();
            RequestContext context = contexts.forPersona(new PersonaScope(new PersonaId("p-lena")), runId);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            transaction.executeWithoutResult(tx -> {
                writer.write(context, new AuditEntry(AuditKind.RESPONSE, first, null, "fixture-1", new Body("a")));
                writer.write(context, new AuditEntry(AuditKind.RESPONSE, second, null, "fixture-1", new Body("b")));
            });
            transaction.executeWithoutResult(tx -> writer.write(
                    contexts.forPersona(new PersonaScope(new PersonaId("p-lena")), unique()),
                    new AuditEntry(AuditKind.RESPONSE, UUID.randomUUID(), null, "fixture-1", new Body("other"))));

            String body = mvc.perform(get("/api/research/audit/export").param("runId", runId)
                            .session(login(mvc, "researcher")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/x-ndjson"))
                    .andReturn().getResponse().getContentAsString();

            List<String> lines = body.lines().toList();
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0)).contains(first.toString()).contains("\"text\":\"a\"");
            assertThat(lines.get(1)).contains(second.toString()).contains("\"text\":\"b\"");
            assertThat(lines.get(0)).startsWith("{\"actionId\":null,\"createdAt\":\"");

            String all = mvc.perform(get("/api/research/audit/export").session(login(mvc, "researcher")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(all.indexOf(first.toString())).isLessThan(all.indexOf(second.toString()));
        }

        @Test
        void onlyTheResearcherMayExport() throws Exception {
            mvc.perform(get("/api/research/audit/export").session(login(mvc, "p-lena")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/research/audit/export").session(login(mvc, "advisor-kaya")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/research/audit/export")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theExportDoesNotExistWithoutTheResearchProfile() throws Exception {
        mvc.perform(get("/api/research/audit/export")).andExpect(status().isNotFound());
    }
}
