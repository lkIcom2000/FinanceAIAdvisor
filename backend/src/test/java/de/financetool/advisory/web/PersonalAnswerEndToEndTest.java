package de.financetool.advisory.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.advisory.domain.RegenerationFix;
import de.financetool.llm.LlmRole;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 13.4 end to end (FR-62): a personal explanation question on a topic with a rule is answered with the
 * persona's own Core figures, at exposure {@code none}. The model saw "generic" (as live on 2026-10-27); grounding
 * makes the question personal, the tool plan adds Lena's emergency-fund figures.
 */
@TestPropertySource(properties = {"agent.compliance=A+B+C", "agent.data-exposure=NONE"})
class PersonalAnswerEndToEndTest extends PostgresIntegrationTest {

    static final String QUESTION = "How much emergency fund should I aim for?";
    static final Pattern FIGURE = Pattern.compile("^\\s+(Emergency fund|Average monthly expenses): (\\S+) EUR$",
            Pattern.MULTILINE);

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void programTheNlu() {
        jdbc.update("DELETE FROM nlu_cache");
        llm.nluAnswer(QUESTION, """
                {"request_type": "general_explanation", "subject": "generic", "topic": "liquidity_management",
                 "product_categories": [], "asserted_premises": [], "instruction_override_attempt": false,
                 "market_timing_request": false, "stated_profile": {}, "computation_params": {},
                 "depersonalized_question": "How large should an emergency fund be?"}""");
    }

    @AfterEach
    void resetTheModel() {
        llm.reset();
    }

    String answer(MockHttpSession session, String text) throws Exception {
        String saved = mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ChatEndToEndTest.body(text)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ChatEndToEndTest.content(mvc.perform(post("/api/chat/messages/{id}/answer",
                (String) JsonPath.read(saved, "$.id")).session(session).with(csrf())).andExpect(status().isOk()));
    }

    Map<String, Object> body(String answer) {
        String payload = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "AND turn_id = ?::uuid", String.class, (String) JsonPath.read(answer, "$.id"));
        return JsonPath.read(payload, "$.body");
    }

    @Test
    void lenasEmergencyFundQuestionIsAnsweredWithHerFiguresAndPassesTheNumberCheck() throws Exception {
        MockHttpSession session = login(mvc, "p-lena");

        // The first turn shows what the prompt holds: her figures as TOOL RESULTS, the instruction not to ask.
        Map<String, Object> first = body(answer(session, QUESTION));
        assertThat(first).containsEntry("class", "INFORM").containsEntry("dataExposure", "NONE");
        assertThat((List<String>) first.get("nluGrounding"))
                .contains("subject set to self: the message asks about the user's own situation");
        String system = llm.calls(LlmRole.PHRASING).getLast().messages().getFirst().content();
        assertThat(system).endsWith("Never ask for financial data the system holds (accounts, income, expenses, "
                + "balances, profile fields).");
        String user = llm.calls(LlmRole.PHRASING).getLast().messages().get(1).content();
        assertThat(user).contains(RegenerationFix.TOOL_RESULTS_HEADER).contains("- emergencyFundTarget:")
                .doesNotContain(QUESTION);
        Matcher figures = FIGURE.matcher(user);
        String current = null;
        String expenses = null;
        while (figures.find()) {
            if (figures.group(1).equals("Emergency fund")) {
                current = figures.group(2);
            } else {
                expenses = figures.group(2);
            }
        }
        assertThat(current).as("current reserve in TOOL RESULTS").isNotNull();
        assertThat(expenses).as("monthly expenses in TOOL RESULTS").isNotNull();
        List<String> evidence = (List<String>) first.get("evidence");
        assertThat(evidence).isNotEmpty();

        // The answer naming her figures is released as INFORM and passes the Number Check.
        String text = "Your emergency fund holds " + current + " EUR against average monthly expenses of " + expenses
                + " EUR.";
        llm.answer(text);
        String released = answer(session, QUESTION);
        assertThat((String) JsonPath.read(released, "$.outcome")).isEqualTo("INFORM");
        assertThat((String) JsonPath.read(released, "$.text")).isEqualTo(text);
        assertThat((List<?>) JsonPath.read(released, "$.figures")).as("no figure chips").isEmpty();
        Map<String, Object> body = body(released);
        assertThat(body).containsEntry("route", "released").containsEntry("finalText", text);
        assertThat((List<String>) body.get("evidence")).containsExactlyElementsOf(evidence);
        List<Map<String, Object>> numberCheck = JsonPath.read(body, "$.finalChecks[?(@.checkId == 'NUMBER_MATCH')]");
        assertThat(numberCheck).singleElement().satisfies(c -> assertThat(c).containsEntry("passed", true));
    }
}
