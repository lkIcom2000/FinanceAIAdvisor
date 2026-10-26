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
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
 * Story 6.2 end to end: an {@code ASK_PROFILE} answer opens the assessment card with the account-derived values
 * pre-filled, Save my answers stores them through the confirm control and answers the original question, a new
 * question cancels the card.
 */
class AssessmentEndToEndTest extends PostgresIntegrationTest {

    static final String PERSONAL = ClassificationEndToEndTest.PERSONAL;
    static final String EXPLANATION = ClassificationEndToEndTest.EXPLANATION;
    static final List<String> CARD = List.of("goal", "investment_horizon_years", "net_income", "monthly_expenses",
            "emergency_fund", "loss_bearing_capacity", "risk_tolerance", "knowledge_experience",
            "sustainability_preference");
    static final String RECOMMENDATION = "A savings plan in a broad equity fund can fit your situation; losses are "
            + "possible and ongoing costs apply.";

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void freshChats() {
        jdbc.update("DELETE FROM nlu_cache");
        for (String table : List.of("dialog_states", "messages", "conversations", "suitability_profiles",
                "needs_profiles")) {
            jdbc.update("DELETE FROM " + table + " WHERE persona_id = 'p-lena'");
        }
        llm.nluAnswer(PERSONAL, """
                {"request_type": "personal_recommendation", "subject": "self", "topic": "savings_plan",
                 "product_categories": ["etf"], "asserted_premises": [], "instruction_override_attempt": false,
                 "market_timing_request": false, "stated_profile": {"requested_monthly_amount": 200},
                 "computation_params": {"monthly_contribution": 200},
                 "depersonalized_question": "Should a saver put a monthly amount into an ETF?"}""");
        llm.nluAnswer(EXPLANATION, """
                {"request_type": "general_explanation", "subject": "generic", "topic": "regulation_or_concept",
                 "product_categories": ["etf"], "asserted_premises": [], "instruction_override_attempt": false,
                 "market_timing_request": false, "stated_profile": {}, "computation_params": {},
                 "depersonalized_question": "What is an ETF?"}""");
    }

    @AfterEach
    void cleanUp() {
        llm.reset();
        jdbc.update("DELETE FROM suitability_profiles WHERE persona_id = 'p-lena'");
        jdbc.update("UPDATE dialog_states SET state = 'NONE', payload = NULL WHERE persona_id = 'p-lena'");
    }

    static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    String ask(MockHttpSession session, String text) throws Exception {
        String saved = content(mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(Map.of("text", text))))
                .andExpect(status().isCreated()));
        return content(mvc.perform(post("/api/chat/messages/{id}/answer", (String) JsonPath.read(saved, "$.id"))
                .session(session).with(csrf())).andExpect(status().isOk()));
    }

    ResultActions save(MockHttpSession session, String cardId, Map<String, String> answers) throws Exception {
        List<Map<String, String>> body = answers.entrySet().stream()
                .map(e -> Map.of("key", e.getKey(), "value", e.getValue())).toList();
        return mvc.perform(post("/api/chat/messages/{id}/assessment", cardId).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(Map.of("answers", body))));
    }

    /** Every card field answered; the pre-filled ones as shown ("Looks right"). */
    static Map<String, String> answers(String card) {
        Map<String, String> out = new LinkedHashMap<>(Map.of("goal", "retirement", "investment_horizon_years", "25",
                "loss_bearing_capacity", "low", "risk_tolerance", "medium", "knowledge_experience", "basic",
                "sustainability_preference", "consider"));
        for (String key : List.of("net_income", "monthly_expenses", "emergency_fund")) {
            List<String> value = JsonPath.read(card, "$.assessment.fields[?(@.key == '" + key + "')].value");
            out.put(key, value.getFirst() == null ? "1000" : value.getFirst());
        }
        return out;
    }

    long profileRows() {
        return jdbc.queryForObject("SELECT count(*) FROM suitability_profiles WHERE persona_id = 'p-lena'",
                Long.class);
    }

    @Test
    void theCardListsEveryMissingFieldPrefillsAccountDataAndSavingAnswersTheQuestion() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        llm.answer("Broad funds spread risk; whether a savings plan fits you depends on your situation.");
        String card = ask(lena, PERSONAL);

        assertThat((String) JsonPath.read(card, "$.outcome")).isEqualTo("ASK_PROFILE");
        assertThat((String) JsonPath.read(card, "$.assessment.status")).isEqualTo("OPEN");
        assertThat((List<String>) JsonPath.read(card, "$.assessment.fields[*].key")).isEqualTo(CARD);
        assertThat((List<String>) JsonPath.read(card, "$.assessment.fields[?(@.provId != null)].key"))
                .containsExactly("net_income", "monthly_expenses", "emergency_fund");
        assertThat((List<String>) JsonPath.read(card, "$.assessment.fields[?(@.key == 'risk_tolerance')].input"))
                .containsExactly("SLIDER");
        String provId = ((List<String>) JsonPath.read(card,
                "$.assessment.fields[?(@.key == 'net_income')].provId")).getFirst();
        mvc.perform(get("/api/provenance/{provId}", provId).session(lena)).andExpect(status().isOk());
        String cardId = JsonPath.read(card, "$.id");
        assertThat(jdbc.queryForObject("SELECT state FROM dialog_states WHERE persona_id = 'p-lena'", String.class))
                .isEqualTo("ASSESSMENT");
        assertThat(jdbc.queryForObject("SELECT payload::text FROM dialog_states WHERE persona_id = 'p-lena'",
                String.class)).contains(cardId).contains("\"fieldQueue\"");
        assertThat(profileRows()).isZero();

        llm.answer(RECOMMENDATION);
        String answer = content(save(lena, cardId, answers(card)).andExpect(status().isOk())
                .andExpect(jsonPath("$.replyTo").value(cardId)));
        assertThat((String) JsonPath.read(answer, "$.outcome")).isEqualTo("RECOMMEND_CATEGORY");
        assertThat(profileRows()).isEqualTo(CARD.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM suitability_profiles WHERE persona_id = 'p-lena' "
                + "AND confirmed_at IS NOT NULL", Long.class)).isEqualTo(CARD.size());
        assertThat(jdbc.queryForObject("SELECT state FROM dialog_states WHERE persona_id = 'p-lena'", String.class))
                .isEqualTo("NONE");
        String chat = content(mvc.perform(get("/api/chat").session(lena)).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(chat, "$.messages[?(@.id == '" + cardId + "')].assessment.status"))
                .containsExactly("SAVED");

        // Saving again returns the same answer without a second turn.
        String again = content(save(lena, cardId, answers(card)).andExpect(status().isOk()));
        assertThat((String) JsonPath.read(again, "$.id")).isEqualTo(JsonPath.read(answer, "$.id"));
    }

    @Test
    void aNewQuestionCancelsTheCardAndSavingItIsRefused() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        llm.answer("Broad funds spread risk; whether a savings plan fits you depends on your situation.");
        String card = ask(lena, PERSONAL);
        String cardId = JsonPath.read(card, "$.id");

        llm.answer("An ETF is a fund traded on an exchange.");
        ask(lena, EXPLANATION);

        String chat = content(mvc.perform(get("/api/chat").session(lena)).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(chat, "$.messages[?(@.id == '" + cardId + "')].assessment.status"))
                .containsExactly("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT state FROM dialog_states WHERE persona_id = 'p-lena'", String.class))
                .isEqualTo("NONE");
        save(lena, cardId, answers(card)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSESSMENT_CLOSED"));
        assertThat(profileRows()).isZero();
    }

    @Test
    void answersThatAreMissingForeignOrDoNotFitStoreNothing() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        llm.answer("Broad funds spread risk; whether a savings plan fits you depends on your situation.");
        String card = ask(lena, PERSONAL);
        String cardId = JsonPath.read(card, "$.id");

        Map<String, String> missing = answers(card);
        missing.remove("goal");
        save(lena, cardId, missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Please answer: What are you saving or investing for?."));

        Map<String, String> foreign = answers(card);
        foreign.put("age", "40");
        save(lena, cardId, foreign).andExpect(status().isBadRequest());

        Map<String, String> wrong = answers(card);
        wrong.put("investment_horizon_years", "a while");
        save(lena, cardId, wrong).andExpect(status().isBadRequest());

        Map<String, String> nul = answers(card);
        nul.put("goal", "retire\u0000ment");
        save(lena, cardId, nul).andExpect(status().isBadRequest());

        save(lena, "not-a-uuid", answers(card)).andExpect(status().isNotFound());
        assertThat(profileRows()).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM dialog_states WHERE persona_id = 'p-lena'", String.class))
                .isEqualTo("ASSESSMENT");
    }
}
