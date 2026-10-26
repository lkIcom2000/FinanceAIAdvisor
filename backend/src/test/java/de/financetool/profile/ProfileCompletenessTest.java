package de.financetool.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Story 6.1: the single completeness implementation against the committed {@code config/profile-minimums.yaml}. */
class ProfileCompletenessTest {

    static final ProfileCompleteness COMPLETENESS = committed();

    @SuppressWarnings("unchecked")
    static ProfileCompleteness committed() {
        try (Reader reader = Files.newBufferedReader(Path.of("..", "config", "profile-minimums.yaml"))) {
            Map<String, Object> root = (Map<String, Object>) ((Map<String, Object>) new Yaml(
                    new SafeConstructor(new LoaderOptions())).load(reader)).get("profileMinimums");
            return new ProfileCompleteness((Map<String, List<List<String>>>) root.get("topics"),
                    (List<String>) root.get("strictSuitabilityFields"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final Map<String, Object> STRICT = Map.of("goal", "retirement", "investment_horizon_years", 20,
            "net_income", 3200, "monthly_expenses", 2500, "emergency_fund", 9000, "loss_bearing_capacity", "medium",
            "risk_tolerance", "medium", "knowledge_experience", "basic", "sustainability_preference", "none");

    @Test
    void aStoredProfileMeetsTheMinimumWithoutLimitationsWhenEveryStrictFieldIsKnown() {
        assertThat(COMPLETENESS.evaluate(STRICT, Map.of(), "savings_plan"))
                .isEqualTo(new ProfileCompleteness.Result(true, List.of(), List.of()));
    }

    @Test
    void missingGroupsAreListedWithTheirAlternatives() {
        assertThat(COMPLETENESS.evaluate(Map.of(), Map.of(), "investment_decision"))
                .isEqualTo(new ProfileCompleteness.Result(false, List.of("risk_tolerance or loss_aversion",
                        "investment_horizon_years", "emergency_fund or monthly_expenses"), List.of()));
    }

    @Test
    void oneFieldOfAGroupSuffices() {
        assertThat(COMPLETENESS.evaluate(Map.of("monthly_surplus", 100), Map.of("loss_aversion", "high"),
                "savings_plan").complete()).isTrue();
    }

    @Test
    void statedFactsCompleteTheStoredProfileForTheTurnAndTheStrictGapsBecomeLimitations() {
        ProfileCompleteness.Result result = COMPLETENESS.evaluate(Map.of("net_income", 3200),
                Map.of("risk_tolerance", "low"), "savings_plan");

        assertThat(result.complete()).isTrue();
        assertThat(result.limitations()).containsExactly("goal", "investment_horizon_years", "monthly_expenses",
                "emergency_fund", "loss_bearing_capacity", "knowledge_experience", "sustainability_preference");
    }

    @Test
    void emptyStatedValuesDoNotOverrideStoredOnes() {
        Map<String, Object> stated = new java.util.HashMap<>();
        stated.put("risk_tolerance", "");
        stated.put("net_income", null);
        stated.put("current_holdings", List.of());

        assertThat(ProfileCompleteness.effective(Map.of("risk_tolerance", "medium", "net_income", 3200), stated))
                .isEqualTo(Map.of("risk_tolerance", "medium", "net_income", 3200));
        assertThat(COMPLETENESS.evaluate(Map.of(), stated, "diversification").complete()).isFalse();
    }

    @Test
    void statedFactsOverrideStoredOnes() {
        assertThat(ProfileCompleteness.effective(Map.of("risk_tolerance", "medium"), Map.of("risk_tolerance", "low")))
                .containsEntry("risk_tolerance", "low");
    }

    @Test
    void anUnknownTopicIsNeverComplete() {
        assertThat(COMPLETENESS.evaluate(STRICT, Map.of(), "regulation_or_concept"))
                .isEqualTo(new ProfileCompleteness.Result(false,
                        List.of(ProfileCompleteness.TOPIC_NOT_SUPPORTED), List.of()));
    }

    @Test
    void needsTopicsHaveNoSuitabilityLimitations() {
        ProfileCompleteness.Result result = COMPLETENESS.evaluate(Map.of("household", "own", "dependants", "none",
                "occupation", "student"), Map.of(), "protection_needs");

        assertThat(result).isEqualTo(new ProfileCompleteness.Result(true, List.of(), List.of()));
    }

    @Test
    void everyMinimumFieldIsAStorableProfileField() {
        Map<String, Object> all = new java.util.HashMap<>();
        ProfileFields.SUITABILITY.forEach(f -> all.put(f, "x"));
        ProfileFields.NEEDS.forEach(f -> all.put(f, "x"));
        for (String topic : List.of("asset_allocation", "savings_plan", "investment_decision", "diversification",
                "short_term_investment", "sustainability_fit", "product_variant_tax", "retirement_strategy",
                "leverage_or_loan", "fund_selection", "liquidity_management", "subsidy_eligibility",
                "contract_switch", "protection_needs", "protection_duplicate")) {
            assertThat(COMPLETENESS.evaluate(all, Map.of(), topic).complete()).as(topic).isTrue();
        }
    }

    @Test
    void strictMissingListsTheAbsentStrictFieldsWhetherOrNotTheMinimumIsMet() {
        assertThat(COMPLETENESS.strictMissing(Map.of(), "savings_plan")).containsExactly("goal",
                "investment_horizon_years", "net_income", "monthly_expenses", "emergency_fund",
                "loss_bearing_capacity", "risk_tolerance", "knowledge_experience", "sustainability_preference");
        assertThat(COMPLETENESS.strictMissing(Map.of("goal", "retirement", "risk_tolerance", "low"),
                "asset_allocation")).doesNotContain("goal", "risk_tolerance").contains("net_income");
        assertThat(COMPLETENESS.strictMissing(STRICT, "savings_plan")).isEmpty();
        assertThat(COMPLETENESS.strictMissing(Map.of(), "protection_needs")).isEmpty();
        assertThat(COMPLETENESS.strictMissing(Map.of(), "market_timing")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void theNeedsTopicsAreExactlyTheYamlTopicsAnsweredFromNeedsFields() throws IOException {
        Map<String, List<List<String>>> topics;
        try (Reader reader = Files.newBufferedReader(Path.of("..", "config", "profile-minimums.yaml"))) {
            Map<String, Object> root = (Map<String, Object>) ((Map<String, Object>) new Yaml(
                    new SafeConstructor(new LoaderOptions())).load(reader)).get("profileMinimums");
            topics = (Map<String, List<List<String>>>) root.get("topics");
        }
        List<String> needsTopics = topics.entrySet().stream()
                .filter(e -> e.getValue().stream().flatMap(List::stream).allMatch(ProfileFields.NEEDS::contains))
                .map(Map.Entry::getKey)
                // A contract switch (e.g. of a pension contract) is a suitability question asked from a needs fact.
                .filter(topic -> !topic.equals("contract_switch"))
                .toList();
        assertThat(ProfileFields.NEEDS_TOPICS).containsExactlyInAnyOrderElementsOf(needsTopics);
        // The assessment card can ask for every field a minimum names, so a saved card always meets it.
        assertThat(topics.values().stream().flatMap(List::stream).flatMap(List::stream).distinct())
                .allSatisfy(field -> assertThat(AssessmentFields.askable(field)).as(field).isTrue());
    }
}
