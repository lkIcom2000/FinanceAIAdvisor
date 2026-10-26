package de.financetool.advisory.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.llm.LlmRole;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import de.financetool.testing.ComplianceLevelSwitch;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Story 6.4: Jonas asks whether he pays twice for liability insurance. Fine asks only the missing Needs Profile facts
 * in one card, then answers with the Core's demands-and-needs statement, which Home shows (UX-DR44).
 */
@TestPropertySource(properties = {"agent.compliance=A+B+C", "agent.data-exposure=AGGREGATED"})
class NeedsStatementEndToEndTest extends PostgresIntegrationTest {

    static final PersonaId JONAS = new PersonaId("p-jonas");
    static final String QUESTION = "Am I paying twice for my liability insurance?";
    static final String DRAFT = "You have your own liability policy and you're also co-insured on your parents' "
            + "family policy. Once you have your own household, the family policy likely no longer covers you, so "
            + "check with your parents and note the cancellation period of your own policy. Your banking data shows "
            + "no occupational disability cover; losing your income through illness is a real risk, so check whether "
            + "you have this cover elsewhere.";

    @Autowired
    StubLlm llm;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ComplianceLevelSwitch.Switch config;

    @Autowired
    Profiles profiles;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    List<PersonaResettable> resettables;

    @BeforeEach
    void jonasWithoutANeedsProfile() {
        reset();
        llm.nluAnswer(QUESTION, """
                {"request_type": "personal_recommendation", "subject": "self", "topic": "protection_duplicate",
                 "product_categories": ["insurance_needs"], "asserted_premises": [], "instruction_override_attempt":
                 false, "market_timing_request": false, "stated_profile": {}, "computation_params": {},
                 "depersonalized_question": "Is a liability risk insured twice?"}""");
    }

    @AfterEach
    void cleanUp() {
        reset();
        llm.reset();
        config.reset();
    }

    /** Jonas's chat, escalation cases and profile, as the research profile's persona reset clears them. */
    void reset() {
        tx.executeWithoutResult(status -> resettables.stream()
                .filter(r -> List.of("advisory", "escalation", "profile").contains(r.module()))
                .forEach(r -> r.reset(JONAS)));
    }

    String ask(MockHttpSession session) throws Exception {
        String saved = mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ChatEndToEndTest.body(QUESTION)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ChatEndToEndTest.content(mvc.perform(post("/api/chat/messages/{id}/answer",
                (String) JsonPath.read(saved, "$.id")).session(session).with(csrf())).andExpect(status().isOk()));
    }

    String save(MockHttpSession session, String cardId, Map<String, String> answers) throws Exception {
        List<Map<String, String>> body = answers.entrySet().stream()
                .map(e -> Map.of("key", e.getKey(), "value", e.getValue())).toList();
        return ChatEndToEndTest.content(mvc.perform(post("/api/chat/messages/{id}/assessment", cardId)
                .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(Map.of("answers", body))))
                .andExpect(status().isOk()));
    }

    @Test
    void theCardAsksOnlyTheNeedsFactsThenTheStatementReachesHome() throws Exception {
        MockHttpSession jonas = login(mvc, "p-jonas");
        mvc.perform(get("/api/chat/needs-statement").session(jonas)).andExpect(status().isNoContent());

        llm.answer("Before I can tell you what cover you need, could you tell me about your household?");
        String card = ask(jonas);
        assertThat((String) JsonPath.read(card, "$.outcome")).isEqualTo("ASK_PROFILE");
        // the contracts come from the banking data: the card never asks for them
        assertThat((List<String>) JsonPath.read(card, "$.assessment.fields[*].key"))
                .containsExactly("occupation", "household", "dependants");

        llm.answer(DRAFT);
        String answer = save(jonas, JsonPath.read(card, "$.id"),
                Map.of("occupation", "Junior developer", "household", "single", "dependants", "0"));

        assertThat((String) JsonPath.read(answer, "$.outcome")).isEqualTo("RECOMMEND_CATEGORY");
        assertThat((String) JsonPath.read(answer, "$.text")).startsWith(ResponseTemplates.NEEDS_LEAD);
        List<Map<String, Object>> checks = JsonPath.read(answer, "$.checks");
        assertThat(checks).isNotEmpty().allSatisfy(c -> assertThat(c).containsEntry("passed", true));
        assertThat(checks).extracting(c -> c.get("id")).contains("IDD_NEEDS", "DOCUMENTATION", "SUITABILITY")
                .doesNotContain("COST_TRANSPARENCY");
        assertThat((Object) JsonPath.read(answer, "$.recommendation")).isNull();

        // chips: liability contracts, occupational disability contracts, risks covered more than once
        assertThat((List<String>) JsonPath.read(answer, "$.figures[*].value")).containsExactly("2", "0", "1");
        assertThat((List<String>) JsonPath.read(answer, "$.needsStatement.areas[*].area"))
                .containsExactly("Liability", "Occupational disability", "Cover held twice");
        assertThat((String) JsonPath.read(answer, "$.needsStatement.areas[0].check"))
                .contains("Parents of Jonas").contains("cancellation period of your own policy");
        assertThat((List<String>) JsonPath.read(answer, "$.needsStatement.facts[*].value"))
                .containsExactly("I live alone", "0", "Junior developer");
        // no provider, product or tariff name anywhere in the answer
        assertThat(answer).doesNotContain("Example Mutual").doesNotContain("Example Family").doesNotContain("EX-");

        String payload = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "AND turn_id = ?::uuid", String.class, (String) JsonPath.read(answer, "$.id"));
        assertThat((Object) JsonPath.read(payload, "$.body.categorySelection")).isNull();
        assertThat((List<String>) JsonPath.read(payload, "$.body.needsStatement.profileFields"))
                .containsExactly("household", "dependants", "occupation");
        List<String> chipIds = JsonPath.read(answer, "$.figures[*].provId");
        assertThat((List<String>) JsonPath.read(payload, "$.body.evidence")).containsAll(chipIds);
        assertThat(llm.calls(LlmRole.PHRASING).getLast().messages().getFirst().content())
                .contains("demands-and-needs statement").doesNotContain("Recommend the product category");

        // Home: the latest statement, every figure with "Why?"
        String home = ChatEndToEndTest.content(mvc.perform(get("/api/chat/needs-statement").session(jonas))
                .andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(home, "$.figures[*].provId")).isEqualTo(chipIds);
        mvc.perform(get("/api/provenance/{provId}", chipIds.getFirst()).session(jonas)).andExpect(status().isOk());
    }

    @Test
    void aDraftNamingAnInsurerIsNotReleased() throws Exception {
        tx.executeWithoutResult(status -> profiles.confirm(new PersonaScope(JONAS),
                Map.of("household", "single", "dependants", 0, "occupation", "Junior developer"),
                Instant.parse("2026-10-26T00:00:00Z")));
        llm.answer(DRAFT + " Allianz sells good liability cover.");
        MockHttpSession jonas = login(mvc, "p-jonas");
        String answer = ask(jonas);
        assertThat((String) JsonPath.read(answer, "$.outcome")).isNotEqualTo("RECOMMEND_CATEGORY");
        assertThat((Object) JsonPath.read(answer, "$.needsStatement")).isNull();
        mvc.perform(get("/api/chat/needs-statement").session(jonas)).andExpect(status().isNoContent());
    }
}
