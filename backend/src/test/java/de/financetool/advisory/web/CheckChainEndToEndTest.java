package de.financetool.advisory.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.advisory.domain.CheckId;
import de.financetool.advisory.domain.NoValidSource;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.llm.LlmRole;
import de.financetool.shared.config.ComplianceLevel;
import de.financetool.testing.ComplianceLevelSwitch;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Story 3.4 end to end, one test per row of the I/O matrix at the level it names (switched per test): Check Chain,
 * Rule Engine and block routing through the chat API, the stored message's checks and the Audit Record. The
 * regeneration row needs a Knowledge Base passage, which no turn has until Epic 4; it is covered by
 * {@code TurnOrchestratorTest} on the stages.
 */
// Pinned to the PRD starting values: these tests cover the aggregated filter and the A+B+C record; the E1-E4
// reference (A+B, exposure none) is checked in AgentConfigurationBindingTest.
@TestPropertySource(properties = {"agent.compliance=A+B+C", "agent.data-exposure=AGGREGATED"})
class CheckChainEndToEndTest extends PostgresIntegrationTest {

    static final String PERSONAL = "Should I put 200 EUR a month into an ETF?";
    static final String PERSONAL_NLU = """
            {"request_type": "personal_recommendation", "subject": "self", "topic": "savings_plan",
             "product_categories": ["etf"], "asserted_premises": [], "instruction_override_attempt": false,
             "market_timing_request": false, "stated_profile": {"requested_monthly_amount": 200},
             "computation_params": {"monthly_contribution": 200},
             "depersonalized_question": "Should a saver put a monthly amount into an ETF?"}""";
    static final String EXPLANATION_NLU = """
            {"request_type": "general_explanation", "subject": "generic", "topic": "regulation_or_concept",
             "product_categories": ["etf"], "asserted_premises": [], "instruction_override_attempt": false,
             "market_timing_request": false, "stated_profile": {}, "computation_params": {},
             "depersonalized_question": "What is an ETF?"}""";

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ComplianceLevelSwitch.Switch config;

    @BeforeEach
    void programTheNlu() {
        jdbc.update("DELETE FROM nlu_cache");
        llm.nluAnswer(ClassificationEndToEndTest.KNOCK_OUT, ClassificationEndToEndTest.KNOCK_OUT_NLU.formatted("false"));
        llm.nluAnswer(PERSONAL, PERSONAL_NLU);
        llm.nluAnswer(ClassificationEndToEndTest.EXPLANATION, EXPLANATION_NLU);
    }

    @AfterEach
    void reset() {
        llm.reset();
        config.reset();
    }

    record Turn(Map<String, Object> answer, Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks() {
            return (List<Map<String, Object>>) answer.get("checks");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> shadow(CheckId id) {
            return ((List<Map<String, Object>>) body.get("shadowChecks")).stream()
                    .filter(c -> c.get("checkId").equals(id.name())).findFirst().orElseThrow();
        }
    }

    Turn turn(String text) throws Exception {
        var lena = login(mvc, "p-lena");
        String saved = mvc.perform(post("/api/chat/messages").session(lena).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ChatEndToEndTest.body(text)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String answer = mvc.perform(post("/api/chat/messages/{id}/answer", (String) JsonPath.read(saved, "$.id"))
                        .session(lena).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String payload = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "AND turn_id = ?::uuid", String.class, (String) JsonPath.read(answer, "$.id"));
        return new Turn(JsonPath.read(answer, "$"), JsonPath.read(payload, "$.body"));
    }

    @Test
    void levelAReleasesAnOutOfScopeRecommendationInShadowModeWithTheFailedScopeRecorded() throws Exception {
        config.set(ComplianceLevel.A);
        llm.answer("I recommend the knock-out certificate on the DAX.");
        Turn t = turn(ClassificationEndToEndTest.KNOCK_OUT);

        assertThat(t.answer()).containsEntry("outcome", "REFUSE_OUT_OF_SCOPE")
                .containsEntry("text", "I recommend the knock-out certificate on the DAX.");
        assertThat(t.body()).containsEntry("shadowMode", true).containsEntry("route", "released")
                .containsEntry("complianceLevel", "A").containsEntry("judgeNotImplemented", false)
                .containsEntry("componentFlags", Map.of("ruleEngine", "SHADOW", "judge", false, "routing", false));
        assertThat(t.shadow(CheckId.SCOPE)).containsEntry("passed", false)
                .containsEntry("reasonCode", "refusal_or_alternative_missing");
        assertThat(t.body().get("finalChecks")).isEqualTo(t.body().get("shadowChecks"));
        assertThat(t.checks()).as("shadow results are not shown on the card").isEmpty();
    }

    @Test
    void outOfScopeAtABReleasesTheRefusalTemplateWithoutFinalChecks() throws Exception {
        config.set(ComplianceLevel.A_B);
        llm.answer("Knock-out certificates are not part of this service.");
        Turn t = turn(ClassificationEndToEndTest.KNOCK_OUT);

        assertThat(t.answer()).containsEntry("outcome", "REFUSE_OUT_OF_SCOPE")
                .containsEntry("text", ResponseTemplates.refuseOutOfScope(List.of("derivative_or_certificate")))
                .containsEntry("checks", List.of());
        assertThat(t.body()).containsEntry("route", "block_scope").containsEntry("firedChecks", List.of("SCOPE"))
                .containsEntry("finalChecks", List.of()).containsEntry("shadowMode", false)
                .containsEntry("routedOnChecks", t.body().get("shadowChecks"))
                .containsEntry("firstDraft", "Knock-out certificates are not part of this service.");
    }

    @Test
    void askProfileAtABTurnsYesInvestIntoTheProfileQuestion() throws Exception {
        config.set(ComplianceLevel.A_B);
        llm.answer("Yes, invest the 200 EUR every month.");
        Turn t = turn(PERSONAL);

        assertThat(t.answer()).containsEntry("outcome", "ASK_PROFILE")
                .containsEntry("text", ResponseTemplates.askProfile(ClassificationEndToEndTest.PERSONAL_MISSING));
        assertThat(t.body()).containsEntry("route", "block_profile")
                .containsEntry("firedChecks", List.of("PROFILE_COMPLETE"));
        assertThat(t.shadow(CheckId.PROFILE_COMPLETE)).containsEntry("reasonCode", "verdict_without_profile");
    }

    @Test
    void informWithRetrievalOnAndNoSourceReleasesTheNoValidSourceTemplateAndChip() throws Exception {
        config.retrieval(true);
        Turn t = turn(ClassificationEndToEndTest.EXPLANATION);

        assertThat(t.answer()).containsEntry("outcome", "INFORM").containsEntry("text", NoValidSource.TEXT)
                .containsEntry("suggestions", List.of(NoValidSource.DEFAULT_SUGGESTION))
                .containsEntry("checks", List.of());
        assertThat(t.body()).containsEntry("route", "no_valid_source").containsEntry("noValidSource", true)
                .containsEntry("firedChecks", List.of("SOURCE_VALIDITY")).containsEntry("regenerated", false);
        assertThat(t.shadow(CheckId.SOURCE_VALIDITY)).containsEntry("reasonCode", "no_valid_source");
        assertThat(llm.calls(LlmRole.PHRASING)).as("no regeneration for a missing source").hasSize(1);
    }

    @Test
    void informWithRetrievalOffReleasesTheCheckedDraftAndSourceValidityDoesNotApply() throws Exception {
        Turn t = turn(ClassificationEndToEndTest.EXPLANATION);

        assertThat(t.answer()).containsEntry("outcome", "INFORM").containsEntry("text", StubLlm.DEFAULT_TEXT)
                .containsEntry("suggestions", List.of());
        assertThat(t.body()).containsEntry("route", "released").containsEntry("noValidSource", false);
        assertThat(t.shadow(CheckId.SOURCE_VALIDITY)).containsEntry("applicable", false);
        assertThat(t.checks()).extracting(c -> c.get("id")).doesNotContain("SOURCE_VALIDITY");
        assertThat(t.body()).containsEntry("citationMarkersRemoved", 0);
    }

    @Test
    void withRetrievalOffAMarkerTheModelInventedIsRemovedFromTheReleasedAnswer() throws Exception {
        llm.answer("An ETF is a fund traded on an exchange [S1].");
        Turn t = turn(ClassificationEndToEndTest.EXPLANATION);

        assertThat(t.answer()).containsEntry("text", "An ETF is a fund traded on an exchange.");
        assertThat(t.body()).containsEntry("citationMarkersRemoved", 1)
                .containsEntry("firstDraft", "An ETF is a fund traded on an exchange [S1].");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNamedProductEscalatesWithTheFiredCheckRecorded() throws Exception {
        llm.answer("iShares Core MSCI World is a good fit. Could you tell me your horizon?");
        Turn t = turn(PERSONAL);

        assertThat(t.answer()).containsEntry("outcome", "ESCALATE").containsEntry("text", ResponseTemplates.ESCALATE)
                .containsEntry("checks", List.of());
        assertThat(t.body()).containsEntry("route", "block_no_product_naming")
                .containsEntry("firedChecks", List.of("NO_PRODUCT_NAMING"));
        assertThat(t.shadow(CheckId.NO_PRODUCT_NAMING)).containsEntry("reasonCode", "product_named");

        // Story 3.5: one TURN case in the same transaction, the card sees no draft and no technical reason
        Map<String, Object> row = jdbc.queryForMap("SELECT id::text AS id, persona_id, origin, question, draft, "
                + "block_reasons::text AS reasons, reason_class, status FROM escalation_cases WHERE turn_id = ?::uuid",
                (String) t.answer().get("id"));
        assertThat(row).containsEntry("persona_id", "p-lena").containsEntry("origin", "TURN")
                .containsEntry("question", PERSONAL)
                .containsEntry("draft", ResponseTemplates.WHY_ASK_PROFILE
                        + " iShares Core MSCI World is a good fit. Could you tell me your horizon?")
                .containsEntry("reasons", "[\"block_no_product_naming\", \"NO_PRODUCT_NAMING:product_named\"]")
                .containsEntry("status", "OPEN");
        assertThat(row.get("reason_class")).isNull();
        assertThat(t.body()).containsEntry("escalationCaseId", row.get("id"));
        assertThat((Map<String, Object>) t.answer().get("handover")).containsEntry("status", "OPEN")
                .containsEntry("reason", null);
        assertThat(t.answer().toString()).doesNotContain("iShares").doesNotContain("product_named");

        String chat = ChatEndToEndTest.content(mvc.perform(get("/api/chat").session(login(mvc, "p-lena"))));
        List<Map<String, Object>> messages = JsonPath.read(chat, "$.messages");
        assertThat(messages.getLast().get("handover")).isEqualTo(t.answer().get("handover"));
        assertThat(messages.get(messages.size() - 2).get("handover")).isNull();
    }

    @Test
    void whenTheAuditWriteOfAnEscalatedTurnFailsNoCaseRemains() throws Exception {
        llm.answer("iShares Core MSCI World is a good fit. Could you tell me your horizon?");
        long cases = jdbc.queryForObject("SELECT count(*) FROM escalation_cases", Long.class);
        // Only the Audit Record carries the digest; PostgreSQL rejects a NUL character in JSONB.
        llm.digest("sha256: ");
        var lena = login(mvc, "p-lena");
        String saved = mvc.perform(post("/api/chat/messages").session(lena).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ChatEndToEndTest.body(PERSONAL)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        mvc.perform(post("/api/chat/messages/{id}/answer", (String) JsonPath.read(saved, "$.id")).session(lena)
                .with(csrf())).andExpect(status().isServiceUnavailable());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM escalation_cases", Long.class)).isEqualTo(cases);
    }

    @Test
    void aReleasedOrTemplatedTurnOpensNoCase() throws Exception {
        config.set(ComplianceLevel.A_B);
        llm.answer("Knock-out certificates are not part of this service.");
        Turn t = turn(ClassificationEndToEndTest.KNOCK_OUT);

        assertThat(t.answer()).containsEntry("outcome", "REFUSE_OUT_OF_SCOPE");
        assertThat(t.answer().get("handover")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM escalation_cases WHERE turn_id = ?::uuid", Long.class,
                (String) t.answer().get("id"))).isZero();
    }

    @Test
    void aCompliantDraftIsReleasedWithItsApplicableChecksAllPassedAndStoredWithTheMessage() throws Exception {
        String draft = "Before I can say what fits, could you tell me your goals, your horizon and whether you have "
                + "an emergency fund?";
        llm.answer(draft);
        Turn t = turn(PERSONAL);

        assertThat(t.answer()).containsEntry("outcome", "ASK_PROFILE")
                .containsEntry("text", ResponseTemplates.WHY_ASK_PROFILE + " " + draft);
        assertThat(t.checks()).extracting(c -> c.get("id")).containsExactly("SCOPE", "PROFILE_COMPLETE",
                "NO_PRODUCT_NAMING", "NO_GUARANTEE_OR_TIMING", "NUMBER_MATCH");
        assertThat(t.checks()).allSatisfy(c -> assertThat(c).containsEntry("passed", true));
        assertThat(t.checks().getFirst())
                .containsEntry("description", CheckId.SCOPE.description());
        assertThat(t.body()).containsEntry("route", "released").containsEntry("firedChecks", List.of())
                .containsEntry("judgeNotImplemented", true)
                .containsEntry("componentFlags", Map.of("ruleEngine", "ENFORCING", "judge", true, "routing", true));

        String chat = ChatEndToEndTest.content(mvc.perform(get("/api/chat").session(login(mvc, "p-lena"))));
        List<Map<String, Object>> messages = JsonPath.read(chat, "$.messages");
        assertThat(messages.getLast()).containsEntry("checks", t.checks());
        assertThat(messages.get(messages.size() - 2)).containsEntry("checks", List.of());
    }
}
