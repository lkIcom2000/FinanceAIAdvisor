package de.financetool.monitoring.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import de.financetool.actions.Actions;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Story 9.1: Lena sets up a rule with explicit limits. */
@ActiveProfiles("research")
class RuleControllerTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final PersonaId MARCO = new PersonaId("p-marco");
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String CUSHION = """
            {"template":"SAFETY_CUSHION","maxPerAction":"300.00","maxPerMonth":"600.00","floor":"2000.00"}""";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ResetPersonaUseCase resetPersona;

    @Autowired
    Actions actions;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    RequestContextFactory contexts;

    MockHttpSession lena;

    @BeforeEach
    void lenaWithoutRules() throws Exception {
        resetPersona.reset(LENA);
        lena = login(mvc, "p-lena");
    }

    /** The test database is shared by every test class: leave the personas as the fixtures define them. */
    @AfterEach
    void restoreFixtureState() {
        Stream.of(LENA, MARCO).forEach(resetPersona::reset);
    }

    String call(String method, String path, String body, MockHttpSession session, ResultMatcher expected)
            throws Exception {
        var request = method.equals("GET") ? get(path)
                : post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request.session(session)).andExpect(expected).andReturn().getResponse()
                .getContentAsString();
    }

    @SuppressWarnings("unchecked")
    <T> T json(String content, Class<?> type) {
        return (T) JSON.readValue(content, type);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setupOffersBothTemplatesWithCoreSuggestedLimitsAndTheirSource() throws Exception {
        List<Map<String, Object>> templates = json(call("GET", "/api/rules/setup", null, lena, status().isOk()),
                List.class);

        assertThat(templates).extracting(t -> t.get("template"))
                .containsExactly("SAFETY_CUSHION", "EMERGENCY_FUND_TOP_UP");
        Map<String, Object> cushion = templates.getFirst();
        assertThat(cushion.get("title")).isEqualTo("Keep a safety cushion in my current account");
        assertThat(cushion.get("fromName")).isEqualTo("Overnight deposit");
        assertThat(cushion.get("toName")).isEqualTo("Current account");
        assertThat(cushion.get("alreadyActive")).isEqualTo(false);
        assertThat((String) cushion.get("source")).startsWith("Suggested by Fine");
        Map<String, Object> perTransfer = (Map<String, Object>) cushion.get("maxPerAction");
        assertThat((String) perTransfer.get("provId")).isNotBlank();
        assertThat(new java.math.BigDecimal((String) perTransfer.get("value"))).isPositive();
        assertThat(templates.get(1).get("fromName")).isEqualTo("Current account");
    }

    @Test
    @SuppressWarnings("unchecked")
    void startingARuleStoresItWithItsAuditRecordAndShowsItsCard() throws Exception {
        Map<String, Object> check = json(call("POST", "/api/rules/check", CUSHION, lena, status().isOk()), Map.class);
        assertThat(check.get("summary")).isEqualTo("Keep my current account above 0 EUR, using my overnight deposit,"
                + " but never let the overnight deposit fall below 2,000 EUR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mandates WHERE persona_id = 'p-lena'", Long.class))
                .isZero();

        Map<String, Object> started = json(call("POST", "/api/rules", CUSHION, lena, status().isCreated()),
                Map.class);

        List<Map<String, Object>> rules = json(call("GET", "/api/rules", null, lena, status().isOk()), List.class);
        assertThat(rules).hasSize(1);
        Map<String, Object> rule = rules.getFirst();
        assertThat(rule.get("id")).isEqualTo(started.get("id"));
        assertThat(rule.get("summary")).isEqualTo(check.get("summary"));
        assertThat(rule.get("activeSince"))
                .isEqualTo(contexts.forPersona(new PersonaScope(LENA), null).asOf().toString());
        assertThat(rule.get("actionCount")).isEqualTo(0);
        assertThat((Map<String, Object>) rule.get("limits")).containsEntry("maxPerAction", "300.00")
                .containsEntry("maxPerMonth", "600.00").containsEntry("floor", "2000.00");
        assertThat(jdbc.queryForObject("SELECT payload->'body'->>'event' FROM audit_records"
                + " WHERE action_id = ?::uuid AND kind = 'ACTION'", String.class, started.get("id")))
                .isEqualTo("rule_created");
        Integer active = transaction.execute(tx -> actions.activeRules(new PersonaScope(LENA)));
        assertThat(active).isEqualTo(1);

        List<Map<String, Object>> templates = json(call("GET", "/api/rules/setup", null, lena, status().isOk()),
                List.class);
        assertThat(templates.getFirst().get("alreadyActive")).isEqualTo(true);
        assertThat(call("POST", "/api/rules", CUSHION, lena, status().isConflict()))
                .contains("You already have a rule like this");
    }

    @Test
    void badLimitsAreRefusedAndNothingIsStored() throws Exception {
        String lowMonth = """
                {"template":"SAFETY_CUSHION","maxPerAction":"300","maxPerMonth":"200","floor":"0"}""";
        assertThat(call("POST", "/api/rules", lowMonth, lena, status().isBadRequest()))
                .contains("The most per month can't be less than the most per transfer.");
        assertThat(call("POST", "/api/rules/check", "{}", lena, status().isBadRequest()))
                .contains("Please choose one of the two rules.");
        assertThat(call("POST", "/api/rules", """
                {"template":"EMERGENCY_FUND_TOP_UP","maxPerAction":"abc","maxPerMonth":"200","floor":"0"}""", lena,
                status().isBadRequest())).contains("Please enter each limit as an amount in EUR.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mandates WHERE persona_id = 'p-lena'", Long.class))
                .isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anotherPersonaSeesNoneOfLenasRulesAndResetRemovesThem() throws Exception {
        call("POST", "/api/rules", CUSHION, lena, status().isCreated());
        MockHttpSession marco = login(mvc, "p-marco");

        assertThat((List<Object>) json(call("GET", "/api/rules", null, marco, status().isOk()), List.class))
                .isEmpty();

        resetPersona.reset(LENA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mandates WHERE persona_id = 'p-lena'", Long.class))
                .isZero();
    }
}
