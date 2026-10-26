package de.financetool.advisory.web;

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
import java.nio.charset.StandardCharsets;
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
 * Story 3.7 end to end with the stubbed model: a {@code COMPUTE} question first gets a parameter proposal, Calculate
 * runs the Core and releases the answer with its figures, a new question cancels an open proposal.
 */
class CalculatedAnswerEndToEndTest extends PostgresIntegrationTest {

    static final String ITEM_40 = "I have a portfolio of 500,000 EUR and want to withdraw 2,000 EUR monthly "
            + "supplemental income. How long does it last at a 4% calculated return?";
    static final String ITEM_40_NLU = "{\"request_type\": \"computation\", \"subject\": \"self\", \"topic\": "
            + "\"withdrawal_duration\", \"computation_params\": {\"portfolio\": 500000, \"monthly_withdrawal\": 2000, "
            + "\"annual_rate\": 0.04}}";
    static final String OWN_SAVINGS = "How long will my savings last if I withdraw 200 EUR a month at a 3% return?";
    static final String OWN_SAVINGS_NLU = "{\"request_type\": \"computation\", \"subject\": \"self\", \"topic\": "
            + "\"withdrawal_duration\", \"computation_params\": {\"monthly_withdrawal\": 200, \"annual_rate\": 0.03}}";
    static final String ANSWER = "It lasts 522 full monthly withdrawals (43.50 years) with an effective rate, or 538 "
            + "with monthly rates, each followed by a smaller final withdrawal. This is a calculation under "
            + "assumptions, not a promise.";

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshChats() {
        jdbc.update("DELETE FROM nlu_cache");
        for (String table : List.of("dialog_states", "messages", "conversations")) {
            jdbc.update("DELETE FROM " + table + " WHERE persona_id IN ('p-lena', 'p-jonas')");
        }
        llm.nluAnswer(ITEM_40, ITEM_40_NLU);
        llm.nluAnswer(OWN_SAVINGS, OWN_SAVINGS_NLU);
    }

    @AfterEach
    void resetTheModel() {
        llm.reset();
    }

    static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    String ask(MockHttpSession session, String text) throws Exception {
        String saved = content(mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(Map.of("text", text))))
                .andExpect(status().isCreated()));
        String id = JsonPath.read(saved, "$.id");
        return content(mvc.perform(post("/api/chat/messages/{id}/answer", id).session(session).with(csrf()))
                .andExpect(status().isOk()));
    }

    ResultActions calculate(MockHttpSession session, String proposalId, String parameters) throws Exception {
        return mvc.perform(post("/api/chat/messages/{id}/calculate", proposalId).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"parameters\": " + parameters + "}"));
    }

    String dialogState(String persona) {
        return jdbc.queryForObject("SELECT state FROM dialog_states WHERE persona_id = ?", String.class, persona);
    }

    @Test
    void item40IsProposedFirstThenCalculatedWithItsFigures() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String proposal = ask(lena, ITEM_40);

        assertThat((String) JsonPath.read(proposal, "$.outcome")).isEqualTo("COMPUTE");
        assertThat((String) JsonPath.read(proposal, "$.proposal.status")).isEqualTo("OPEN");
        assertThat((List<String>) JsonPath.read(proposal, "$.proposal.parameters[*].value"))
                .containsExactly("500000.00", "2000.00", "0.04");
        assertThat((List<String>) JsonPath.read(proposal, "$.proposal.parameters[*].source"))
                .containsOnly("QUESTION");
        assertThat((List<Object>) JsonPath.read(proposal, "$.checks")).isEmpty();
        assertThat(llm.calls(LlmRole.PHRASING)).as("nothing is phrased before Calculate").isEmpty();
        assertThat(dialogState("p-lena")).isEqualTo("PARAMETER_PROPOSAL");
        String proposalId = JsonPath.read(proposal, "$.id");

        llm.answer(ANSWER);
        String answer = content(calculate(lena, proposalId, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPUTE"))
                .andExpect(jsonPath("$.replyTo").value(proposalId))
                .andExpect(jsonPath("$.text").value(ANSWER)));
        assertThat((List<String>) JsonPath.read(answer, "$.figures[*].value"))
                .containsExactly("522", "43.50", "24.66", "538", "44.83", "847.26");
        assertThat(dialogState("p-lena")).isEqualTo("NONE");
        String provId = JsonPath.read(answer, "$.figures[0].provId");
        mvc.perform(get("/api/provenance/{provId}", provId).session(lena)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT origin FROM evidence_items WHERE prov_id = ?", String.class, provId))
                .isEqualTo("turn");

        String chat = content(mvc.perform(get("/api/chat").session(lena)).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(chat, "$.messages[?(@.id == '" + proposalId + "')].proposal.status"))
                .containsExactly("CALCULATED");

        calculate(lena, proposalId, "[]").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(answer, "$.id")));
        assertThat(llm.calls(LlmRole.PHRASING)).as("calculating twice returns the stored answer").hasSize(1);
    }

    @Test
    void changedValuesAreCalculatedAndTaggedAndInvalidOnesAreRefused() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String proposalId = JsonPath.read(ask(lena, ITEM_40), "$.id");

        calculate(lena, proposalId, "[{\"key\": \"annualRate\", \"value\": \"0.5\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        llm.answer("It lasts a while with these values.");
        calculate(lena, proposalId, "[{\"key\": \"monthlyWithdrawal\", \"value\": \"2500\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPUTE"));
        String body = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "ORDER BY seq DESC LIMIT 1", String.class);
        assertThat((List<String>) JsonPath.read(body, "$.body.simulationSpec.parameters[*].source"))
                .containsExactly("QUESTION", "CHANGED", "QUESTION");
    }

    @Test
    void aNewQuestionCancelsTheOpenProposal() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String proposalId = JsonPath.read(ask(lena, ITEM_40), "$.id");

        ask(lena, "What is an ETF?");

        assertThat(dialogState("p-lena")).isEqualTo("NONE");
        calculate(lena, proposalId, "[]").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_CLOSED"));
        String chat = content(mvc.perform(get("/api/chat").session(lena)));
        assertThat((List<String>) JsonPath.read(chat, "$.messages[?(@.id == '" + proposalId + "')].proposal.status"))
                .containsExactly("CANCELLED");
    }

    /**
     * On a simulated date of its own: evidence items are append-only and keep the origin of their first writer, and
     * {@code WealthOverviewEndToEndTest} expects Jonas' figures of his fixture date to come from the Overview.
     */
    @Test
    void jonasSavingsComeFromHisAccountDataWithTheOverviewFigureKey() throws Exception {
        java.sql.Date fixtureDate = jdbc.queryForObject(
                "SELECT simulated_date FROM persona_state WHERE persona_id = 'p-jonas'", java.sql.Date.class);
        jdbc.update("UPDATE persona_state SET simulated_date = simulated_date + 3 WHERE persona_id = 'p-jonas'");
        try {
            jonasOnAnotherDay();
        } finally {
            jdbc.update("UPDATE persona_state SET simulated_date = ? WHERE persona_id = 'p-jonas'", fixtureDate);
        }
    }

    void jonasOnAnotherDay() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        String proposal = ask(jonas, OWN_SAVINGS);

        assertThat((String) JsonPath.read(proposal, "$.proposal.parameters[0].source")).isEqualTo("ACCOUNT_DATA");
        String provId = JsonPath.read(proposal, "$.proposal.parameters[0].provId");
        String wealth = content(mvc.perform(get("/api/overview/wealth").session(jonas)).andExpect(status().isOk()));
        assertThat(wealth).contains(provId);

        llm.answer("Your savings last a long time under these assumptions.");
        String answer = content(calculate(jonas, JsonPath.read(proposal, "$.id"), "[]").andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(answer, "$.figures[*].provId")).contains(provId);
    }

    @Test
    void anotherPersonasProposalIsNotFound() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String proposalId = JsonPath.read(ask(lena, ITEM_40), "$.id");

        calculate(login(mvc, "p-jonas"), proposalId, "[]").andExpect(status().isNotFound());
        calculate(lena, "not-a-uuid", "[]").andExpect(status().isNotFound());
    }
}
