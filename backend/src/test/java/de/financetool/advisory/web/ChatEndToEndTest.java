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
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.ConfigFingerprint;
import de.financetool.testing.StubLlmConfiguration;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 3.1: the chat API end to end, one test per row of the I/O matrix, with the stubbed model. Since Story 3.3 an
 * {@code INFORM} turn releases the no-valid-source template (no Knowledge Base yet) and keeps the draft as first
 * draft.
 */
// Pinned to the PRD starting values: these tests cover the aggregated filter and the A+B+C record; the E1-E4
// reference (A+B, exposure none) is checked in AgentConfigurationBindingTest.
@TestPropertySource(properties = {"agent.compliance=A+B+C", "agent.data-exposure=AGGREGATED"})
class ChatEndToEndTest extends PostgresIntegrationTest {

    static final String QUESTION = "What is an ETF?";
    static final String FAILED =
            "Something went wrong on our side, so I couldn't finish the answer. Your question is saved.";

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ConfigFingerprint fingerprint;

    /** The NLU answer of a personal request: {@code ASK_PROFILE} for a message about the user (stub incomplete). */
    static final String PERSONAL_NLU = "{\"request_type\": \"personal_recommendation\", \"subject\": \"self\", "
            + "\"topic\": \"savings_plan\"}";

    @BeforeEach
    void forgetCachedExtractions() {
        jdbc.update("DELETE FROM nlu_cache");
    }

    @AfterEach
    void resetTheModel() {
        llm.reset();
    }

    static String body(String text) {
        return JsonMapper.builder().build().writeValueAsString(Map.of("text", text));
    }

    ResultActions postMessage(MockHttpSession session, String text) throws Exception {
        return mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(text)));
    }

    ResultActions answer(MockHttpSession session, String id) throws Exception {
        return mvc.perform(post("/api/chat/messages/{id}/answer", id).session(session).with(csrf()));
    }

    String ask(MockHttpSession session, String text) throws Exception {
        String saved = postMessage(session, text).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(saved, "$.id");
    }

    static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    long responseRecords(String turnId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_records WHERE kind = 'RESPONSE' AND turn_id = ?::uuid",
                Long.class, turnId);
    }

    long allRecords() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM audit_records) + (SELECT count(*) FROM "
                + "evidence_items)", Long.class);
    }

    long replies(String messageId) {
        return jdbc.queryForObject("SELECT count(*) FROM messages WHERE reply_to = ?::uuid", Long.class, messageId);
    }

    @Test
    void lenaAsksAndGetsOneCompleteAuditedAnswer() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String saved = content(postMessage(lena, QUESTION).andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("USER"))
                .andExpect(jsonPath("$.text").value(QUESTION)));
        String questionId = JsonPath.read(saved, "$.id");

        String answer = content(answer(lena, questionId).andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("AGENT"))
                .andExpect(jsonPath("$.outcome").value("INFORM"))
                .andExpect(jsonPath("$.text").value(StubLlm.DEFAULT_TEXT))
                .andExpect(jsonPath("$.suggestions.length()").value(0))
                .andExpect(jsonPath("$.checks[0].id").value("SCOPE"))
                .andExpect(jsonPath("$.checks[0].passed").value(true))
                .andExpect(jsonPath("$.checks.length()").value(4))
                .andExpect(jsonPath("$.replyTo").value(questionId)));
        String turnId = JsonPath.read(answer, "$.id");

        // One RESPONSE record for Lena, sharing the agent message's id as turnId.
        Map<String, Object> record = jdbc.queryForMap("SELECT persona_id, fixture_version, payload::text AS payload "
                + "FROM audit_records WHERE kind = 'RESPONSE' AND turn_id = ?::uuid", UUID.fromString(turnId));
        assertThat(record.get("persona_id")).isEqualTo("p-lena");
        assertThat(record.get("fixture_version")).isEqualTo(jdbc.queryForObject(
                "SELECT fixture_version FROM persona_state WHERE persona_id = 'p-lena'", String.class));
        Map<String, Object> body = JsonPath.read((String) record.get("payload"), "$.body");
        assertThat(body).containsEntry("class", "INFORM").containsEntry("outcome", "INFORM")
                .containsEntry("evidence", List.of()).containsEntry("route", "released")
                .containsEntry("modelTag", StubLlm.MODEL_TAG).containsEntry("modelDigest", StubLlm.DIGEST)
                .containsEntry("promptHashes", promptHashes())
                .containsEntry("truncated", false).containsEntry("droppedHistoryTurns", 0)
                .containsEntry("droppedPassages", 0).containsEntry("dataExposure", "AGGREGATED")
                .containsEntry("messageWithheld", false).containsEntry("firstDraft", StubLlm.DEFAULT_TEXT)
                .containsEntry("finalText", StubLlm.DEFAULT_TEXT).containsEntry("noValidSource", false)
                .containsEntry("regenerated", false)
                .containsEntry("complianceLevel", "A+B+C")
                .containsEntry("passThroughStages", List.of("DIALOG_STATE", "TOOL_PLAN", "KB_RETRIEVE", "JUDGE"))
                .containsEntry("shadowMode", false).containsEntry("judgeNotImplemented", true)
                .containsEntry("classReasons", List.of("general explanation"))
                .containsEntry("nluModelTag", StubLlm.MODEL_TAG).containsEntry("nluModelDigest", StubLlm.DIGEST)
                .containsEntry("nluPromptHashes", Map.of(
                        "nlu-system.txt", fingerprint.fileHashes().get("config/prompts/nlu-system.txt"),
                        "nlu-few-shot.json", fingerprint.fileHashes().get("config/prompts/nlu-few-shot.json")))
                .containsEntry("nluCacheHit", false).containsEntry("nluParseOk", true)
                .containsEntry("instructionOverrideAttempt", false)
                .doesNotContainKeys("judgeVerdict", "judgeParseStatus");
        assertThat(JsonPath.<String>read((String) record.get("payload"), "$.body.nlu.request_type")).isEqualTo("other");

        // One NLU call, then one phrasing call: role prompt, compliance rules and class instruction as system
        // message, the question only in the slot.
        assertThat(llm.calls()).extracting(StubLlmConfiguration.Call::role).containsExactly(LlmRole.NLU,
                LlmRole.PHRASING);
        assertThat(llm.calls(LlmRole.PHRASING)).singleElement().satisfies(call -> {
            assertThat(call.role()).isEqualTo(LlmRole.PHRASING);
            assertThat(call.messages()).hasSize(2);
            assertThat(call.messages().get(0).author()).isEqualTo(LlmMessage.Author.SYSTEM);
            assertThat(call.messages().get(0).content()).doesNotContain(QUESTION).contains("financial advisor")
                    .contains("Rules you must follow:", "7. Tone: plain language")
                    .endsWith("Answer type: general explanation. Explain neutrally; do not give a personal "
                            + "recommendation. When TOOL RESULTS hold the user's own figures, explain with them. "
                            + "Never ask for financial data the system holds (accounts, income, expenses, balances, "
                            + "profile fields).");
            assertThat(call.messages().get(1).content()).endsWith("<user_message>\n" + QUESTION + "\n</user_message>");
        });

        // The conversation lists both, oldest first; it survives a new login.
        String chat = content(mvc.perform(get("/api/chat").session(login(mvc, "p-lena"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.personaName").value("Lena")));
        List<String> ids = JsonPath.read(chat, "$.messages[*].id");
        assertThat(ids).containsSubsequence(questionId, turnId);
        assertThat(ids.indexOf(turnId)).isEqualTo(ids.indexOf(questionId) + 1);
    }

    Map<String, String> promptHashes() {
        return promptHashes("INFORM");
    }

    Map<String, String> promptHashes(String messageClass) {
        String classFile = "class-" + messageClass + ".txt";
        return Map.of("role-advisor.txt", fingerprint.fileHashes().get("config/prompts/role-advisor.txt"),
                "compliance-rules.txt", fingerprint.fileHashes().get("config/prompts/compliance-rules.txt"),
                classFile, fingerprint.fileHashes().get("config/prompts/" + classFile));
    }

    /** Asks and answers {@code question}; the stubbed model answers {@code answer}. */
    void turn(MockHttpSession session, String question, String answer) throws Exception {
        llm.answer(answer);
        answer(session, ask(session, question)).andExpect(status().isOk());
    }

    String lastUserPart() {
        return llm.calls(LlmRole.PHRASING).getLast().messages().get(1).content();
    }

    @Test
    void theThirdQuestionSeesOnlyThePreviousTurnFilteredInNluAndPhrasingAndTheRecordHoldsBothPromptHashes()
            throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        llm.nluAnswer("I'm 34 and have 12,400 EUR saved.", PERSONAL_NLU);
        turn(lena, "What is an ETF?", "An ETF is a fund traded on an exchange.");
        turn(lena, "I'm 34 and have 12,400 EUR saved.", "Thanks, noted your savings. What are your goals?");

        String questionId = ask(lena, "Should I start a savings plan?");
        llm.answer("Here is how a savings plan works.");
        String answer = content(answer(lena, questionId).andExpect(status().isOk()));

        String user = lastUserPart();
        String earlier = "EARLIER CONVERSATION (oldest first):\n"
                + "Customer: I'm 30s and have 10,000\u201315,000 EUR saved.\n"
                + "Assistant: " + ResponseTemplates.WHY_ASK_PROFILE
                + " Thanks, noted your savings. What are your goals?";
        assertThat(user).endsWith(earlier + "\n\n<user_message>\nShould I start a savings plan?\n</user_message>")
                .doesNotContain("What is an ETF?").doesNotContain("traded on an exchange")
                .doesNotContain("12,400").doesNotContain("Lena").doesNotContain("p-lena");
        assertThat(llm.calls(LlmRole.NLU).getLast().messages().getLast().content())
                .as("the NLU sees the same previous turn")
                .isEqualTo(earlier + "\n\n<user_message>Should I start a savings plan?</user_message>");
        assertThat(llm.calls(LlmRole.PHRASING).getLast().messages().get(0).content())
                .contains("Rules you must follow:")
                .doesNotContain("Should I start").doesNotContain("noted your savings")
                .doesNotContain("traded on an exchange").doesNotContain("12,400");

        String payload = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "AND turn_id = ?::uuid", String.class, (String) JsonPath.read(answer, "$.id"));
        Map<String, Object> body = JsonPath.read(payload, "$.body");
        assertThat(body).containsEntry("promptHashes", promptHashes());
        assertThat(promptHashes().values()).doesNotContainNull();
    }

    @Test
    void anotherPersonasTurnsNeverReachTheHistory() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        MockHttpSession jonas = login(mvc, "p-jonas");
        turn(lena, "lena question one", "lena answer one");
        turn(jonas, "JONAS-SECRET question", "JONAS-SECRET answer");
        turn(lena, "lena question two", "lena answer two");
        turn(lena, "lena question three", "lena answer three");
        turn(jonas, "JONAS-SECRET second question", "JONAS-SECRET second answer");

        turn(lena, "lena current question", "lena current answer");

        String user = lastUserPart();
        assertThat(user).isEqualTo("EARLIER CONVERSATION (oldest first):\n"
                + "Customer: lena question three\nAssistant: lena answer three\n\n"
                + "<user_message>\nlena current question\n</user_message>");
        assertThat(user).doesNotContain("JONAS-SECRET");
    }

    @Test
    void theHistoryIsTheOneLastReleasedTurnWithoutUnansweredQuestions() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        for (int i = 1; i <= 5; i++) {
            // digit-free answers: "question 4" is a reference, not a sourced number, for the Number Check
            turn(jonas, "question " + i, "answer " + (char) ('a' + i - 1));
        }
        ask(jonas, "unanswered question");

        turn(jonas, "current question", "current answer");

        String user = lastUserPart();
        assertThat(user).contains("EARLIER CONVERSATION (oldest first):\n"
                + "Customer: question 5\nAssistant: answer e\n\n"
                + "<user_message>\ncurrent question\n</user_message>")
                .doesNotContain("question 4").doesNotContain("unanswered");
    }

    @Test
    void answeringTwiceReturnsTheStoredAnswerWithoutASecondTurn() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String questionId = ask(lena, QUESTION);

        String first = content(answer(lena, questionId).andExpect(status().isOk()));
        llm.answer("A different text that must not appear.");
        String second = content(answer(lena, questionId).andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        assertThat(llm.calls()).hasSize(2);
        assertThat(responseRecords(JsonPath.read(first, "$.id"))).isEqualTo(1);
        assertThat(replies(questionId)).isEqualTo(1);
    }

    @Test
    void anotherPersonasMessageIdIsNotFoundAndNothingIsWritten() throws Exception {
        String questionId = ask(login(mvc, "p-lena"), QUESTION);
        long records = allRecords();

        answer(login(mvc, "p-jonas"), questionId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        answer(login(mvc, "p-jonas"), UUID.randomUUID().toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(content(answer(login(mvc, "p-jonas"), "p-lena").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND")))).doesNotContain("p-lena");

        assertThat(replies(questionId)).isZero();
        assertThat(allRecords()).isEqualTo(records);
        assertThat(llm.calls()).isEmpty();
    }

    @Test
    void whenTheModelIsDownTheQuestionStaysSavedAndUnanswered() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String questionId = ask(lena, QUESTION);
        long records = allRecords();
        llm.fail(new IllegalStateException("Ollama at http://localhost:11434 does not answer"));

        String problem = content(answer(lena, questionId).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TURN_FAILED"))
                .andExpect(jsonPath("$.detail").value(FAILED)));
        assertThat(problem).doesNotContain("Ollama").doesNotContain("Exception").doesNotContain("11434");
        assertThat(replies(questionId)).isZero();
        assertThat(allRecords()).isEqualTo(records);

        String chat = content(mvc.perform(get("/api/chat").session(lena)).andExpect(status().isOk()));
        List<Map<String, Object>> messages = JsonPath.read(chat, "$.messages");
        assertThat(messages.getLast()).containsEntry("id", questionId).containsEntry("author", "USER");

        // Try again repeats only the answer call.
        llm.reset();
        answer(lena, questionId).andExpect(status().isOk()).andExpect(jsonPath("$.replyTo").value(questionId));
        assertThat(replies(questionId)).isEqualTo(1);
    }

    @Test
    void whenTheAuditWriteFailsNothingIsReleased() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String questionId = ask(lena, QUESTION);
        long records = allRecords();
        // Only the Audit Record carries the digest; PostgreSQL rejects a NUL character in JSONB.
        llm.digest("sha256:\u0000");

        answer(lena, questionId).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TURN_FAILED"));

        assertThat(llm.calls()).as("NLU and phrasing ran").hasSize(2);
        assertThat(replies(questionId)).isZero();
        assertThat(allRecords()).as("no Audit Record, no evidence").isEqualTo(records);
    }

    @Test
    void blankOrOverlongTextIsRejectedAndNothingIsSaved() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        long messages = jdbc.queryForObject("SELECT count(*) FROM messages", Long.class);

        for (String text : List.of("", "   \n ", "x".repeat(1001))) {
            postMessage(lena, text).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(post("/api/chat/messages").session(lena).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM messages", Long.class)).isEqualTo(messages);

        postMessage(lena, "x".repeat(1000)).andExpect(status().isCreated());
    }

    @Test
    void theChatIsOnlyForPersonas() throws Exception {
        mvc.perform(get("/api/chat")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chat").session(login(mvc, "advisor-kaya"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/messages").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(QUESTION))).andExpect(status().isUnauthorized());
    }
}
