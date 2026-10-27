package de.financetool.monitoring.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import de.financetool.monitoring.Monitoring;
import de.financetool.monitoring.domain.NotificationTemplates;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Story 8.4: Lena reviews and confirms the shortfall transfer from a real monitoring run. */
@ActiveProfiles("research")
class ActionControllerTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final PersonaId MARCO = new PersonaId("p-marco");
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String NO_RULE = "This needs your OK because you haven't set up a rule for this.";

    @Autowired
    Monitoring monitoring;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ResetPersonaUseCase resetPersona;

    UUID actionId;
    MockHttpSession lena;

    @BeforeEach
    void lenaAfterAMonitoringRun() throws Exception {
        resetPersona.reset(LENA);
        PersonaScope scope = new PersonaScope(LENA);
        Monitoring.Run run = transaction.execute(tx -> monitoring.run(
                contexts.forPersona(scope, "run-" + UUID.randomUUID()), scope));
        actionId = run.actions().getFirst().actionId();
        lena = login(mvc, "p-lena");
    }

    /** The test database is shared by every test class: leave the personas as the fixtures define them. */
    @AfterEach
    void restoreFixtureState() {
        Stream.of(LENA, MARCO).forEach(resetPersona::reset);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> call(String method, String path, MockHttpSession session, ResultMatcher expected)
            throws Exception {
        String json = mvc.perform((method.equals("GET") ? get(path) : post(path).with(csrf())).session(session))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
        return JSON.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> shortfallNotification() throws Exception {
        String json = mvc.perform(get("/api/notifications").session(lena))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> items = JSON.readValue(json, List.class);
        return items.stream().filter(i -> actionId.toString().equals(i.get("actionId"))).findFirst().orElseThrow();
    }

    long bankPortBookings() {
        return jdbc.queryForObject("SELECT count(*) FROM transactions WHERE persona_id = ? AND origin = 'BANK_PORT'",
                Long.class, LENA.value());
    }

    List<String> auditOutcomes() {
        return jdbc.queryForList("SELECT payload->'body'->>'outcome' FROM audit_records"
                + " WHERE action_id = ? AND kind = 'ACTION' ORDER BY seq", String.class, actionId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theReviewShowsTheExactTransferWithBalancesBeforeAndAfterAndThePlainReason() throws Exception {
        Map<String, Object> review = call("GET", "/api/actions/" + actionId + "/review", lena, status().isOk());

        BigDecimal amount = jdbc.queryForObject("SELECT amount FROM actions WHERE id = ?", BigDecimal.class,
                actionId);
        assertThat(review.get("amountText")).isEqualTo(NotificationTemplates.eur(amount));
        assertThat(review.get("sentence")).isEqualTo("Move " + review.get("amountText")
                + " from Overnight deposit to Current account");
        assertThat((String) review.get("amountText")).matches("[0-9,]+\\.[0-9]{2} EUR");
        Map<String, Object> from = (Map<String, Object>) review.get("from");
        Map<String, Object> to = (Map<String, Object>) review.get("to");
        assertThat(from.get("name")).isEqualTo("Overnight deposit");
        assertThat(new BigDecimal((String) from.get("after")))
                .isEqualTo(new BigDecimal((String) from.get("before")).subtract(amount));
        assertThat(new BigDecimal((String) to.get("after")))
                .isEqualTo(new BigDecimal((String) to.get("before")).add(amount));
        assertThat(review.get("reason")).isEqualTo(NO_RULE);
        assertThat((String) review.get("why")).isNotBlank().doesNotContainIgnoringCase("mandate");

        Map<String, Object> item = shortfallNotification();
        assertThat(item.get("status")).isEqualTo("NEEDS_YOUR_OK");
        assertThat(item.get("reason")).isEqualTo(NO_RULE);
    }

    @Test
    void confirmingBooksTheTransferOnceOnTheSameRowWithItsAuditRecord() throws Exception {
        long actionsBefore = jdbc.queryForObject("SELECT count(*) FROM actions", Long.class);

        Map<String, Object> result = call("POST", "/api/actions/" + actionId + "/confirm", lena, status().isOk());

        assertThat(result.get("done")).isEqualTo(true);
        assertThat(result).doesNotContainKeys("reason", "why");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM actions", Long.class)).isEqualTo(actionsBefore);
        assertThat(jdbc.queryForObject("SELECT state FROM actions WHERE id = ?", String.class, actionId))
                .isEqualTo("EXECUTED");
        assertThat(bankPortBookings()).isEqualTo(2);
        assertThat(auditOutcomes()).containsExactly(null, "executed");
        assertThat(shortfallNotification().get("status")).isEqualTo("DONE_FOR_YOU");

        call("POST", "/api/actions/" + actionId + "/confirm", lena, status().isConflict());
        call("GET", "/api/actions/" + actionId + "/review", lena, status().isConflict());
        assertThat(bankPortBookings()).isEqualTo(2);
    }

    @Test
    void aFailedReCheckKeepsTheActionWaitingWithTheUpdatedReason() throws Exception {
        jdbc.update("UPDATE accounts SET opening_balance = opening_balance - 100000 WHERE persona_id = ?"
                + " AND type = 'OVERNIGHT'", LENA.value());

        Map<String, Object> result = call("POST", "/api/actions/" + actionId + "/confirm", lena, status().isOk());

        assertThat(result.get("done")).isEqualTo(false);
        assertThat((String) result.get("reason"))
                .startsWith("I can't make this transfer because your overnight deposit holds only ");
        assertThat((String) result.get("why")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT state FROM actions WHERE id = ?", String.class, actionId))
                .isEqualTo("AWAITING_CONFIRMATION");
        assertThat(bankPortBookings()).isZero();
        assertThat(auditOutcomes()).containsExactly(null, "refused");
        Map<String, Object> item = shortfallNotification();
        assertThat(item.get("status")).isEqualTo("NEEDS_YOUR_OK");
        assertThat(item.get("reason")).isEqualTo(result.get("reason"));
    }

    @Test
    void anotherPersonasOrAMalformedIdAnswersLikeAnUnknownOne() throws Exception {
        MockHttpSession marco = login(mvc, "p-marco");
        for (String path : List.of("/api/actions/" + actionId, "/api/actions/" + UUID.randomUUID(),
                "/api/actions/not-a-uuid")) {
            call("GET", path + "/review", marco, status().isNotFound());
            call("POST", path + "/confirm", marco, status().isNotFound());
        }
        assertThat(jdbc.queryForObject("SELECT state FROM actions WHERE id = ?", String.class, actionId))
                .isEqualTo("AWAITING_CONFIRMATION");
        assertThat(bankPortBookings()).isZero();
    }
}
