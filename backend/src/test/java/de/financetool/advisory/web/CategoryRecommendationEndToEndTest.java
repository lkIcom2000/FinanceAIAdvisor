package de.financetool.advisory.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.llm.LlmRole;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import de.financetool.testing.ComplianceLevelSwitch;
import de.financetool.testing.StubLlmConfiguration.StubLlm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
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

/**
 * Story 6.3: Marco's equity ETF plan with a complete stored profile gets a Category Recommendation that says the plan
 * doesn't fit, with the Core's selection, costs and Suitability Statement (UX-DR43).
 */
@TestPropertySource(properties = {"agent.compliance=A+B+C", "agent.data-exposure=AGGREGATED"})
class CategoryRecommendationEndToEndTest extends PostgresIntegrationTest {

    static final PersonaId MARCO = new PersonaId("p-marco");
    static final String QUESTION = "I want to invest 400 EUR per month in an equity ETF. Is that a good idea?";
    static final String DRAFT = "Your monthly surplus is about 100 EUR and your emergency fund of 300 EUR is below "
            + "its target of 6,000 EUR. Build the emergency fund first, then start a small savings plan in a broad "
            + "world equity fund (ETF) that matches your surplus. Equity prices can fall, so losses are possible, "
            + "and ongoing costs apply.";

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

    @BeforeEach
    void marcoWithACompleteProfile() {
        jdbc.update("DELETE FROM nlu_cache");
        llm.nluAnswer(QUESTION, """
                {"request_type": "personal_recommendation", "subject": "self", "topic": "savings_plan",
                 "product_categories": ["etf"], "asserted_premises": [], "instruction_override_attempt": false,
                 "market_timing_request": false, "stated_profile": {"requested_monthly_amount": 400},
                 "computation_params": {}, "depersonalized_question": "Is a monthly equity ETF plan a good idea?"}""");
        // as the assessment card stores it after Marco confirmed the pre-filled account figures
        Map<String, Object> profile = new HashMap<>(Map.of("goal", "wealth_building", "investment_horizon_years", 15,
                "net_income", 2100, "monthly_expenses", 2000, "emergency_fund", 300, "loss_bearing_capacity", "low",
                "risk_tolerance", "medium", "knowledge_experience", "basic", "sustainability_preference", "none"));
        tx.executeWithoutResult(status -> profiles.confirm(new PersonaScope(MARCO), profile,
                Instant.parse("2026-10-26T00:00:00Z")));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM suitability_profiles");
        llm.reset();
        config.reset();
    }

    String ask(MockHttpSession session) throws Exception {
        String saved = mvc.perform(post("/api/chat/messages").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ChatEndToEndTest.body(QUESTION)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ChatEndToEndTest.content(mvc.perform(post("/api/chat/messages/{id}/answer",
                (String) JsonPath.read(saved, "$.id")).session(session).with(csrf())).andExpect(status().isOk()));
    }

    @Test
    void thePlanDoesNotFitAndTheCardExplainsWhyWithTheCoresFigures() throws Exception {
        llm.answer(DRAFT);
        MockHttpSession marco = login(mvc, "p-marco");
        String answer = ask(marco);

        assertThat((String) JsonPath.read(answer, "$.outcome")).isEqualTo("RECOMMEND_CATEGORY");
        assertThat((String) JsonPath.read(answer, "$.text")).startsWith("This plan doesn't fit right now: the "
                + "emergency fund is below its target; the monthly amount is more than what is left each month.");
        List<Map<String, Object>> checks = JsonPath.read(answer, "$.checks");
        assertThat(checks).isNotEmpty().allSatisfy(c -> assertThat(c).containsEntry("passed", true));
        assertThat(checks).extracting(c -> c.get("id")).contains("SUITABILITY", "COST_TRANSPARENCY",
                "NO_PRODUCT_NAMING", "CONFLICT_OF_INTEREST", "DOCUMENTATION");

        // chips: the surplus and the emergency fund; the latter is the Liquidity tab's figure
        List<String> chipValues = JsonPath.read(answer, "$.figures[*].value");
        assertThat(chipValues).containsExactly("100.00", "300.00");
        String liquidity = mvc.perform(get("/api/overview/liquidity").session(marco)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(answer, "$.figures[1].provId"))
                .isEqualTo(JsonPath.read(liquidity, "$.emergencyFund.current.provId"));

        assertThat((String) JsonPath.read(answer, "$.recommendation.category"))
                .isEqualTo("build the emergency fund first");
        assertThat((List<String>) JsonPath.read(answer, "$.recommendation.conflicts")).hasSize(2);
        assertThat((List<String>) JsonPath.read(answer, "$.recommendation.facts[*].label"))
                .contains("Monthly net income", "Emergency fund", "Monthly amount you have in mind");
        assertThat((List<String>) JsonPath.read(answer, "$.recommendation.figures[*].value"))
                .contains("100.00", "300.00", "6000.00", "400.00");
        assertThat((List<String>) JsonPath.read(answer, "$.recommendation.risks")).isNotEmpty();
        assertThat((List<String>) JsonPath.read(answer, "$.recommendation.costs[*].label"))
                .contains("ETF costs over 10 years", "Active fund costs over 10 years");

        String payload = jdbc.queryForObject("SELECT payload::text FROM audit_records WHERE kind = 'RESPONSE' "
                + "AND turn_id = ?::uuid", String.class, (String) JsonPath.read(answer, "$.id"));
        Map<String, Object> selection = JsonPath.read(payload, "$.body.categorySelection");
        assertThat(selection).containsEntry("category", "build_emergency_fund_first");
        assertThat((Map<String, Object>) selection.get("selectionInputs"))
                .containsEntry("profileFields", List.of("investment_horizon_years", "risk_tolerance",
                        "emergency_fund", "monthly_expenses", "net_income", "requested_monthly_amount"))
                .containsEntry("ruleParameters", List.of("emergency_fund_months_min", "short_horizon_years_max"));
        // AC1: every selection and cost figure is a Core result in the evidence and the ledger
        List<String> statementIds = JsonPath.read(answer, "$.recommendation.figures[*].provId");
        List<String> costIds = JsonPath.read(answer, "$.recommendation.costs[*].provId");
        assertThat(costIds).hasSize(7);
        List<String> evidence = JsonPath.read(payload, "$.body.evidence");
        assertThat(evidence).containsAll(statementIds).containsAll(costIds);
        List<String> ledgerLabels = JsonPath.read(payload, "$.body.numberLedger[*].label");
        assertThat(ledgerLabels).containsAll(costIds).contains((String) JsonPath.read(answer,
                "$.figures[0].provId"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence_items WHERE prov_id = ANY(?::text[])",
                Integer.class, (Object) costIds.toArray(String[]::new))).isEqualTo(7);
        assertThat(llm.calls(LlmRole.PHRASING).getLast().messages().getFirst().content())
                .contains("build emergency fund first")
                .contains("The request conflicts with the profile (emergency fund below target; requested monthly "
                        + "amount exceeds monthly surplus)");
    }

    @Test
    void aDraftNamingAnIssuerIsNotReleased() throws Exception {
        llm.answer(DRAFT + " The iShares Core MSCI World is a good choice.");
        String answer = ask(login(mvc, "p-marco"));
        assertThat((String) JsonPath.read(answer, "$.outcome")).isNotEqualTo("RECOMMEND_CATEGORY");
        assertThat((Object) JsonPath.read(answer, "$.recommendation")).isNull();
    }
}
