package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rule-based category selection, ported from {@code select_category} of {@code experiments/finlab/core.py}. */
class CategorySelectionTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 15);
    static final Map<String, String> VALUES = Map.ofEntries(Map.entry("short_horizon_years_max", "3"),
            Map.entry("emergency_fund_months_min", "3"),
            Map.entry("equity_share_very_low_min", "0.0"), Map.entry("equity_share_very_low_max", "0.1"),
            Map.entry("equity_share_low_min", "0.1"), Map.entry("equity_share_low_max", "0.3"),
            Map.entry("equity_share_medium_min", "0.4"), Map.entry("equity_share_medium_max", "0.6"),
            Map.entry("equity_share_high_min", "0.7"), Map.entry("equity_share_high_max", "0.9"),
            Map.entry("equity_share_very_high_min", "0.8"), Map.entry("equity_share_very_high_max", "1.0"));

    static Map<String, RuleParameterValue> parameters() {
        Map<String, RuleParameterValue> out = new HashMap<>();
        VALUES.forEach((k, v) -> out.put(k, new RuleParameterValue(k, new BigDecimal(v), "1",
                LocalDate.of(2020, 1, 1), null)));
        return out;
    }

    /** Marco after the assessment card: stored amounts come back as doubles, risk tolerance with a space. */
    static Map<String, Object> marco() {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("net_income", 2100.0);
        profile.put("monthly_expenses", 2000.0);
        profile.put("emergency_fund", 300.0);
        profile.put("investment_horizon_years", 15);
        profile.put("risk_tolerance", "medium");
        profile.put("loss_aversion", "medium");
        profile.put("goal", "wealth_building");
        return profile;
    }

    static CategorySelection.Selected select(Map<String, ?> profile, String topic) {
        return CategorySelection.of(profile, topic, AS_OF, parameters());
    }

    static BigDecimal output(CategorySelection.Selected selected, String key) {
        return selected.computation().outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst()
                .map(Figure::value).orElse(null);
    }

    @Test
    void marcosPlanConflictsWithTheEmergencyFundAndTheSurplus() {
        Map<String, Object> profile = marco();
        profile.put("requested_monthly_amount", 400);
        CategorySelection.Selected selected = select(profile, "savings_plan");

        assertThat(selected.selection().category()).isEqualTo(CategorySelection.BUILD_EMERGENCY_FUND_FIRST);
        assertThat(selected.selection().conflicts()).containsExactly(CategorySelection.EMERGENCY_FUND_BELOW_TARGET,
                CategorySelection.REQUESTED_EXCEEDS_SURPLUS);
        assertThat(output(selected, CategorySelection.OUTPUT_SURPLUS)).isEqualByComparingTo("100.00");
        assertThat(output(selected, CategorySelection.OUTPUT_EMERGENCY_FUND)).isEqualByComparingTo("300.00");
        assertThat(output(selected, CategorySelection.OUTPUT_EMERGENCY_FUND_TARGET)).isEqualByComparingTo("6000.00");
        assertThat(output(selected, CategorySelection.OUTPUT_REQUESTED)).isEqualByComparingTo("400.00");
        assertThat(output(selected, CategorySelection.OUTPUT_CONFLICTS)).isEqualByComparingTo("2");
        assertThat(selected.selection().profileFields()).containsExactly("investment_horizon_years",
                "risk_tolerance", "loss_aversion", "emergency_fund", "monthly_expenses", "net_income",
                "requested_monthly_amount");
        assertThat(selected.selection().ruleParameters()).containsExactly("emergency_fund_months_min",
                "short_horizon_years_max");
        assertThat(selected.computation().keyInputs()).doesNotContainKey("goal");
        assertThat(selected.selection().label()).isEqualTo("build the emergency fund first");
        assertThat(selected.selection().risks()).isNotEmpty();
    }

    @Test
    void anAdequateFundGivesAnEquityCategoryWithTheBucketsShareRange() {
        Map<String, Object> profile = marco();
        profile.put("emergency_fund", 6000.0);
        profile.put("risk_tolerance", "very high");
        CategorySelection.Selected high = select(profile, "savings_plan");
        assertThat(high.selection().category()).isEqualTo("diversified_low_cost_world_equity_etf_savings_plan");
        assertThat(high.selection().riskBucket()).isEqualTo("very_high");
        assertThat(output(high, CategorySelection.OUTPUT_EQUITY_MIN)).isEqualByComparingTo("0.8");
        assertThat(output(high, CategorySelection.OUTPUT_EQUITY_MAX)).isEqualByComparingTo("1.0");
        assertThat(high.selection().conflicts()).isEmpty();

        profile.remove("risk_tolerance");
        CategorySelection.Selected unknown = select(profile, "asset_allocation");
        assertThat(unknown.selection().category()).isEqualTo("balanced_etf_portfolio");
        assertThat(output(unknown, CategorySelection.OUTPUT_EQUITY_MIN)).isEqualByComparingTo("0.4");
        assertThat(unknown.selection().reasons()).containsExactly("Risk tolerance 'medium' with horizon 15 years");
    }

    @Test
    void shortHorizonLowRiskAndTopicRulesComeFirstInTheExperimentsOrder() {
        Map<String, Object> profile = marco();
        profile.put("investment_horizon_years", 3);
        assertThat(select(profile, "savings_plan").selection().category())
                .isEqualTo("short_term_deposit_or_money_market");
        assertThat(select(profile, "diversification").selection().category())
                .isEqualTo("broad_world_equity_etf_for_diversification");

        Map<String, Object> cautious = marco();
        cautious.put("emergency_fund", 6000.0);
        cautious.put("loss_aversion", "high");
        assertThat(select(cautious, "savings_plan").selection().category()).isEqualTo("deposit_or_low_risk_mix");

        Map<String, Object> loan = marco();
        assertThat(select(loan, "leverage_or_loan").selection())
                .satisfies(s -> assertThat(s.category()).isEqualTo("no_credit_financed_investing"))
                .satisfies(s -> assertThat(s.conflicts()).containsExactly(
                        CategorySelection.EMERGENCY_FUND_BELOW_TARGET));
    }

    @Test
    void topicRulesWinOverAnAdequateFundAndRiskTextMatchesTheExperimentsBucketOrder() {
        Map<String, Object> profile = marco();
        profile.put("emergency_fund", 6000.0);
        assertThat(select(profile, "product_variant_tax").selection().category())
                .isEqualTo("distributing_or_accumulating_with_unused_allowance");
        assertThat(select(profile, "sustainability_fit").selection().category())
                .isEqualTo("sustainable_diversified_etf");
        assertThat(select(profile, "leverage_or_loan").selection().conflicts()).isEmpty();
        assertThat(CategorySelection.bucket("Very Low")).isEqualTo("very_low");
        assertThat(CategorySelection.bucket("low to medium")).isEqualTo("low");
        assertThat(CategorySelection.bucket("none")).isNull();
        profile.put("investment_horizon_years", "4");
        profile.put("net_income", "2100");
        CategorySelection.Selected text = select(profile, "savings_plan");
        assertThat(text.selection().category()).isEqualTo("balanced_etf_portfolio");
        assertThat(text.computation().inputs()).filteredOn(i -> i.label().equals("net income"))
                .singleElement().satisfies(i -> assertThat(i.value()).isEqualByComparingTo("2100"));
    }

    @Test
    void anEmptyProfileStillSelectsAndRecordsOnlyTheConflictCount() {
        CategorySelection.Selected selected = select(Map.of(), "protection_duplicate");
        assertThat(selected.selection().category()).isEqualTo("review_duplicate_cover");
        assertThat(selected.computation().outputs()).extracting(Figure::outputKey)
                .containsExactly(CategorySelection.OUTPUT_CONFLICTS);
    }

    @Test
    void aMissingRuleParameterIsRefused() {
        Map<String, RuleParameterValue> parameters = parameters();
        parameters.remove("equity_share_high_max");
        assertThatThrownBy(() -> CategorySelection.of(marco(), "savings_plan", AS_OF, parameters))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** FR-27: no commission, inducement, operator preference or product input exists in the signature. */
    @Test
    void theSelectionsOnlyInputsAreTheProfileTheTopicTheDateAndRuleParameters() {
        List<Method> selections = Arrays.stream(CategorySelection.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("of") && Modifier.isPublic(m.getModifiers())).toList();
        assertThat(selections).hasSize(1);
        assertThat(selections.getFirst().getParameterTypes())
                .containsExactly(Map.class, String.class, LocalDate.class, Map.class);
        assertThat(selections.getFirst().getGenericParameterTypes()[3].getTypeName())
                .isEqualTo("java.util.Map<java.lang.String, de.financetool.core.domain.RuleParameterValue>");
    }
}
