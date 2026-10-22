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
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.ConfigFingerprint;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Story 3.1: the chat API end to end, one test per row of the I/O matrix, with the stubbed model. */
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
                .containsEntry("evidence", List.of()).containsEntry("checks", List.of())
                .containsEntry("modelTag", StubLlm.MODEL_TAG).containsEntry("modelDigest", StubLlm.DIGEST)
                .containsEntry("promptHashes", promptHashes())
                .containsEntry("truncated", false).containsEntry("droppedHistoryTurns", 0)
                .containsEntry("droppedPassages", 0).containsEntry("firstDraft", StubLlm.DEFAULT_TEXT)
                .containsEntry("finalText", StubLlm.DEFAULT_TEXT).containsEntry("regenerated", false)
                .containsEntry("complianceLevel", "A+B+C")
                .containsEntry("passThroughStages", List.of("DIALOG_STATE", "NLU", "CLASSIFY", "TOOL_PLAN",
                        "KB_RETRIEVE", "SHADOW_CHECKS", "RULE_ENGINE", "JUDGE", "BLOCK_ROUTING"))
                .doesNotContainKeys("judgeVerdict", "judgeParseStatus");

        // One phrasing call: role prompt and compliance rules as system message, the question only in the slot.
        assertThat(llm.calls()).singleElement().satisfies(call -> {
            assertThat(call.role()).isEqualTo(LlmRole.PHRASING);
            assertThat(call.messages()).hasSize(2);
            assertThat(call.messages().get(0).author()).isEqualTo(LlmMessage.Author.SYSTEM);
            assertThat(call.messages().get(0).content()).doesNotContain(QUESTION).contains("financial advisor")
                    .contains("Rules you must follow:", "7. Tone: plain language");
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
        return Map.of("role-advisor.txt", fingerprint.fileHashes().get("config/prompts/role-advisor.txt"),
                "compliance-rules.txt", fingerprint.fileHashes().get("config/prompts/compliance-rules.txt"));
    }

    /** Asks and answers {@code question}; the stubbed model answers {@code answer}. */
    void turn(MockHttpSession session, String question, String answer) throws Exception {
        llm.answer(answer);
        answer(session, ask(session, question)).andExpect(status().isOk());
    }

    String lastUserPart() {
        return llm.calls().getLast().messages().get(1).content();
    }

    @Test
    void theThirdQuestionSeesBothEarlierTurnsFilteredAndTheRecordHoldsBothPromptHashes() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        turn(lena, "I'm 34 and have 12,400 EUR saved.", "Thanks, noted your savings.");
        turn(lena, "What is an ETF?", "An ETF is a fund traded on an exchange.");

        String questionId = ask(lena, "Should I start a savings plan?");
        llm.answer("Here is how a savings plan works.");
        String answer = content(answer(lena, questionId).andExpect(status().isOk()));

        String user = lastUserPart();
        assertThat(user).contains("EARLIER CONVERSATION (oldest first):")
                .contains("Customer: I'm 30s and have 10,000\u201315,000 EUR saved.\n"
                        + "Assistant: Thanks, noted your savings.\n"
                        + "Customer: What is an ETF?\nAssistant: An ETF is a fund traded on an exchange.\n\n"
                        + "<user_message>\nShould I start a savings plan?\n</user_message>")
                .doesNotContain("12,400").doesNotContain("Lena").doesNotContain("p-lena");
        assertThat(llm.calls().getLast().messages().get(0).content()).contains("Rules you must follow:")
                .doesNotContain("Should I start").doesNotContain("noted your savings")
                .doesNotContain("traded on an exchange").doesNotContain("12,400");

        String payload = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "AND turn_id = ?::uuid", String.class, (String) JsonPath.read(answer, "$.id"));
        Map<String, Object> body = JsonPath.read(payload, "$.body");
        assertThat(body).containsEntry("promptHashes", promptHashes());
        assertThat(promptHashes().values()).doesNotContainNull();
    }

    @Test
    void theHistoryIsTheLastThreeReleasedTurnsWithoutUnansweredQuestions() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        for (int i = 1; i <= 5; i++) {
            turn(jonas, "question " + i, "answer " + i);
        }
        ask(jonas, "unanswered question");

        turn(jonas, "current question", "current answer");

        String user = lastUserPart();
        assertThat(user).contains("EARLIER CONVERSATION (oldest first):\n"
                + "Customer: question 3\nAssistant: answer 3\n"
                + "Customer: question 4\nAssistant: answer 4\n"
                + "Customer: question 5\nAssistant: answer 5\n\n"
                + "<user_message>\ncurrent question\n</user_message>")
                .doesNotContain("question 2").doesNotContain("unanswered");
    }

    @Test
    void answeringTwiceReturnsTheStoredAnswerWithoutASecondTurn() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String questionId = ask(lena, QUESTION);

        String first = content(answer(lena, questionId).andExpect(status().isOk()));
        llm.answer("A different text that must not appear.");
        String second = content(answer(lena, questionId).andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        assertThat(llm.calls()).hasSize(1);
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

        assertThat(llm.calls()).hasSize(1);
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
