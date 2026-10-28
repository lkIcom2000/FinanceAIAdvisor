package de.financetool.advisory.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.advisory.domain.ExposureFilter;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.ConfigFingerprint;
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
 * Story 13.5 end to end (FR-63), at exposure {@code none}: Lena's emergency-fund answer, then "Why?". The NLU and the
 * phrasing prompt get the one previous turn as its depersonalised question and Fine's phrased answer, never the raw
 * message; an answer quoting the earlier Core figure passes the Number Check, sourced as {@code CORE}. A template
 * previous turn shows only its figures.
 */
@TestPropertySource(properties = {"agent.compliance=A+B+C", "agent.data-exposure=NONE"})
class FollowUpEndToEndTest extends PostgresIntegrationTest {

    static final String QUESTION = "How much emergency fund should I aim for?";
    static final String DEPERSONALISED = "How large should an emergency fund be?";
    static final String WHY = "Why?";
    static final String OIL = "Which oil futures should I buy today?";
    static final Pattern FIGURE = Pattern.compile("^\\s+Emergency fund: (\\S+) EUR$", Pattern.MULTILINE);

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ConfigFingerprint fingerprint;

    @BeforeEach
    void programTheNlu() {
        jdbc.update("DELETE FROM nlu_cache");
        llm.nluAnswer(QUESTION, """
                {"request_type": "general_explanation", "subject": "generic", "topic": "liquidity_management",
                 "product_categories": [], "asserted_premises": [], "instruction_override_attempt": false,
                 "market_timing_request": false, "stated_profile": {}, "computation_params": {},
                 "depersonalized_question": "How large should an emergency fund be?"}""");
        llm.nluAnswer(WHY, """
                {"request_type": "general_explanation", "subject": "generic", "topic": "other",
                 "product_categories": [], "asserted_premises": [], "instruction_override_attempt": false,
                 "market_timing_request": false, "stated_profile": {}, "computation_params": {},
                 "depersonalized_question": "Why is an emergency fund of that size recommended?"}""");
        llm.nluAnswer(OIL, """
                {"request_type": "product_selection", "subject": "self", "topic": "market_timing",
                 "product_categories": ["derivative_or_certificate"], "asserted_premises": [],
                 "instruction_override_attempt": false, "market_timing_request": true, "stated_profile": {},
                 "computation_params": {}, "depersonalized_question": "Which oil futures should be bought today?"}""");
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

    String lastNluMessage() {
        return llm.calls(LlmRole.NLU).getLast().messages().getLast().content();
    }

    String lastPhrasingUserPart() {
        return llm.calls(LlmRole.PHRASING).getLast().messages().get(1).content();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFollowUpSeesTheDepersonalisedQuestionAndThePhrasedAnswerAndMayQuoteItsFigure() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");

        // Lena's reserve as the Core computes it, then her answer naming it.
        answer(lena, QUESTION);
        Matcher figure = FIGURE.matcher(lastPhrasingUserPart());
        assertThat(figure.find()).as("her reserve in TOOL RESULTS").isTrue();
        String reserve = figure.group(1);
        String phrased = "Your emergency fund holds " + reserve + " EUR.";
        llm.answer(phrased);
        String first = answer(lena, QUESTION);
        assertThat((String) JsonPath.read(first, "$.outcome")).isEqualTo("INFORM");
        List<String> evidence = (List<String>) body(first).get("evidence");

        // "Why?": one previous turn, the same in the NLU and the phrasing prompt, no raw message.
        String because = "Because the " + reserve + " EUR you hold is your safety buffer.";
        llm.answer(because);
        String followUp = answer(lena, WHY);
        String earlier = "EARLIER CONVERSATION (oldest first):\nCustomer: " + DEPERSONALISED + "\nAssistant: "
                + phrased;
        assertThat(lastNluMessage()).isEqualTo(earlier + "\n\n<user_message>" + WHY + "</user_message>");
        assertThat(lastPhrasingUserPart()).isEqualTo(earlier + "\n\n<user_message>\n"
                + "Why is an emergency fund of that size recommended?\n</user_message>");
        assertThat(llm.calls(LlmRole.NLU).getLast().messages().toString()).doesNotContain(QUESTION);
        assertThat(lastPhrasingUserPart()).doesNotContain(QUESTION).doesNotContain("Lena");

        // The answer quoting the earlier figure is released; the ledger sources it as CORE.
        assertThat((String) JsonPath.read(followUp, "$.outcome")).isEqualTo("INFORM");
        assertThat((String) JsonPath.read(followUp, "$.text")).isEqualTo(because);
        Map<String, Object> body = body(followUp);
        assertThat(body).containsEntry("route", "released").containsEntry("droppedHistoryTurns", 0);
        List<Map<String, Object>> numberCheck = JsonPath.read(body, "$.finalChecks[?(@.checkId == 'NUMBER_MATCH')]");
        assertThat(numberCheck).singleElement().satisfies(c -> assertThat(c).containsEntry("passed", true));
        List<String> coreLabels = JsonPath.read(body, "$.numberLedger[?(@.source == 'CORE')].label");
        assertThat(coreLabels).as("the previous turn's figures, as stored").isNotEmpty().isSubsetOf(evidence);
        List<String> labels = JsonPath.read(body, "$.numberLedger[*].label");
        assertThat(labels).doesNotContain("numbers in an earlier user message");

        // The changed prompt files are hashed into the record.
        assertThat((Map<String, String>) body.get("nluPromptHashes")).containsEntry("nlu-system.txt",
                fingerprint.fileHashes().get("config/prompts/nlu-system.txt"));
        assertThat((Map<String, String>) body.get("promptHashes")).containsEntry("role-advisor.txt",
                fingerprint.fileHashes().get("config/prompts/role-advisor.txt"));
    }

    @Test
    void aTemplatePreviousTurnShowsOnlyItsFigures() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String refusal = answer(lena, OIL);
        assertThat((String) JsonPath.read(refusal, "$.outcome")).isNotIn("INFORM", "COMPUTE", "ASK_PROFILE");
        String refusalText = JsonPath.read(refusal, "$.text");

        answer(lena, "Okay?");

        String user = lastPhrasingUserPart();
        assertThat(user).startsWith("EARLIER CONVERSATION (oldest first):\n"
                + "Customer: Which oil futures should be bought today?\nAssistant: "
                + ExposureFilter.TEMPLATE_FIGURES.formatted(ExposureFilter.NO_FIGURES) + "\n\n");
        assertThat(user).doesNotContain(refusalText).doesNotContain(OIL);
        assertThat(lastNluMessage()).startsWith("EARLIER CONVERSATION (oldest first):\n"
                + "Customer: Which oil futures should be bought today?\nAssistant: [fixed answer")
                .doesNotContain(refusalText);
    }
}
