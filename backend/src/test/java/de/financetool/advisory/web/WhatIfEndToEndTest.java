package de.financetool.advisory.web;

import static de.financetool.advisory.web.CalculatedAnswerEndToEndTest.content;
import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.llm.LlmRole;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 7.1 end to end with the stubbed model: Jonas's what-if is proposed first (amount, duration, subsidy with
 * eligibility from account data), Calculate runs the Core's what-if projection and releases the fixed range sentence
 * with the range chart; nothing is phrased by the model. Runs on a simulated date of its own (see
 * {@link CalculatedAnswerEndToEndTest}: evidence keeps the origin of its first writer).
 */
class WhatIfEndToEndTest extends PostgresIntegrationTest {

    static final String QUESTION = "What if I save 150 EUR a month for 30 years, with the state subsidy?";
    static final String NLU = "{\"request_type\": \"computation\", \"subject\": \"self\", \"topic\": "
            + "\"savings_projection\", \"computation_params\": {\"monthly_contribution\": 150, \"years\": 30}}";
    static final String RANGE = "Between [\\d,]+\\.\\d\\d and [\\d,]+\\.\\d\\d EUR after 30 years\\. The middle outcome is "
            + "[\\d,]+\\.\\d\\d EUR\\. This is a range, not a promise\\.";

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshChats() {
        jdbc.update("DELETE FROM nlu_cache");
        for (String table : List.of("dialog_states", "messages", "conversations")) {
            jdbc.update("DELETE FROM " + table + " WHERE persona_id = 'p-jonas'");
        }
        llm.nluAnswer(QUESTION, NLU);
    }

    @AfterEach
    void resetTheModel() {
        llm.reset();
    }

    String ask(MockHttpSession session, String text) throws Exception {
        String saved = content(mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(Map.of("text", text))))
                .andExpect(status().isCreated()));
        return content(mvc.perform(post("/api/chat/messages/{id}/answer", (String) JsonPath.read(saved, "$.id"))
                .session(session).with(csrf())).andExpect(status().isOk()));
    }

    ResultActions calculate(MockHttpSession session, String proposalId, String parameters) throws Exception {
        return mvc.perform(post("/api/chat/messages/{id}/calculate", proposalId).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"parameters\": " + parameters + "}"));
    }

    int whatIfEvidence() {
        return jdbc.queryForObject("SELECT count(*) FROM evidence_items WHERE persona_id = 'p-jonas' "
                + "AND content::text LIKE '%whatIfProjection%'", Integer.class);
    }

    @Test
    void jonasSeesTheParametersFirstThenARangeWithItsAssumptions() throws Exception {
        java.sql.Date fixtureDate = jdbc.queryForObject(
                "SELECT simulated_date FROM persona_state WHERE persona_id = 'p-jonas'", java.sql.Date.class);
        jdbc.update("UPDATE persona_state SET simulated_date = simulated_date + 5 WHERE persona_id = 'p-jonas'");
        try {
            jonasOnAnotherDay();
        } finally {
            jdbc.update("UPDATE persona_state SET simulated_date = ? WHERE persona_id = 'p-jonas'", fixtureDate);
        }
    }

    void jonasOnAnotherDay() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        int before = whatIfEvidence();
        String proposal = ask(jonas, QUESTION);

        assertThat((String) JsonPath.read(proposal, "$.outcome")).isEqualTo("COMPUTE");
        assertThat((Boolean) JsonPath.read(proposal, "$.proposal.range")).isTrue();
        assertThat((List<String>) JsonPath.read(proposal, "$.proposal.parameters[*].key"))
                .containsExactly("monthlyAmount", "years", "subsidy");
        assertThat((List<String>) JsonPath.read(proposal, "$.proposal.parameters[*].value"))
                .containsExactly("150.00", "30", "1");
        assertThat((List<String>) JsonPath.read(proposal, "$.proposal.parameters[*].unit"))
                .containsExactly("EUR", "YEARS", "FLAG");
        assertThat((List<String>) JsonPath.read(proposal, "$.proposal.parameters[*].source"))
                .containsExactly("QUESTION", "QUESTION", "ACCOUNT_DATA");
        assertThat(whatIfEvidence()).as("nothing is computed before Calculate").isEqualTo(before);
        String proposalId = JsonPath.read(proposal, "$.id");

        calculate(jonas, proposalId, "[{\"key\": \"subsidy\", \"value\": \"0\"}]")
                .andExpect(status().isBadRequest());
        String answer = content(calculate(jonas, proposalId, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPUTE"))
                .andExpect(jsonPath("$.replyTo").value(proposalId)));
        assertThat((String) JsonPath.read(answer, "$.text")).matches(RANGE);
        assertThat((List<Object>) JsonPath.read(answer, "$.range.points")).hasSize(30);
        assertThat((List<String>) JsonPath.read(answer, "$.range.assumptions"))
                .anyMatch(a -> a.contains("riester_basic_allowance version 2018"))
                .anyMatch(a -> a.startsWith("Returns:"))
                .anyMatch(a -> a.startsWith("Costs:"))
                .anyMatch(a -> a.startsWith("Inflation:"))
                .anyMatch(a -> a.startsWith("Horizon: 30 years"));
        assertThat((List<String>) JsonPath.read(answer, "$.figures[*].value")).contains("54000.00", "5250.00");
        assertThat(llm.calls(LlmRole.PHRASING)).as("the range is fixed text").isEmpty();
        String provId = JsonPath.read(answer, "$.range.provId");
        mvc.perform(get("/api/provenance/{provId}", provId).session(jonas)).andExpect(status().isOk());

        // The same spec again gives the same figures (same seed, same keys).
        String again = content(calculate(jonas, JsonPath.read(ask(jonas, QUESTION), "$.id"), "[]")
                .andExpect(status().isOk()));
        assertThat((String) JsonPath.read(again, "$.text")).isEqualTo(JsonPath.read(answer, "$.text"));
        assertThat((String) JsonPath.read(again, "$.range.provId")).isEqualTo(provId);
    }
}
