package de.financetool.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.CheckChain;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.CategorySelection;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.Figure;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.core.domain.RecommendationFigures;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import de.financetool.testing.TestContexts;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Story 6.3: the Suitability Statement, the Check Chain's view of it and the figure chips. */
class RecommendationsTest {

    final CoreGateway core = mock(CoreGateway.class);
    final Recommendations recommendations = new Recommendations(core);

    static RecommendationFigures recommendation(CoreResult costs) {
        CoreResult selection = new CoreResult(CategorySelection.FUNCTION, List.of(),
                List.of(new Figure(CategorySelection.OUTPUT_CONFLICTS, "Conflicts", BigDecimal.ONE, Unit.COUNT),
                        new Figure(CategorySelection.OUTPUT_SURPLUS, "Monthly surplus", new BigDecimal("100.00"),
                                Unit.EUR),
                        new Figure(CategorySelection.OUTPUT_EMERGENCY_FUND, "Emergency fund", new BigDecimal("300.00"),
                                Unit.EUR)),
                List.of(), Map.of(),
                List.of(number("bbbbbbbbbbbbbbb0", "1"), number("bbbbbbbbbbbbbbb1", "100.00"),
                        number("bbbbbbbbbbbbbbb2", "300.00")));
        return new RecommendationFigures(selection, new CategorySelection.Selection(
                CategorySelection.BUILD_EMERGENCY_FUND_FIRST, List.of("Emergency fund below target"),
                List.of(CategorySelection.EMERGENCY_FUND_BELOW_TARGET), "medium",
                List.of("risk_tolerance", "emergency_fund", "investment_horizon_years"),
                List.of("emergency_fund_months_min")), costs);
    }

    static SourcedNumber number(String provId, String value) {
        return new SourcedNumber(provId, new BigDecimal(value), Unit.EUR, "label");
    }

    void accountFund(String value) {
        CoreResult fund = mock(CoreResult.class);
        when(fund.number(CoreGateway.EMERGENCY_FUND)).thenReturn(number("aaaaaaaaaaaaaaa3", value));
        when(core.profileFigures(any(), any())).thenReturn(new ProfileFigures(mock(CoreResult.class), fund));
    }

    @Test
    void theStatementListsTheProfileFactsInWordsTheFiguresAndTheRisks() {
        ChatMessage.Recommendation statement = Recommendations.statement(recommendation(null),
                Map.of("risk_tolerance", "very low", "emergency_fund", 300.0, "investment_horizon_years", 15,
                        "goal", "retirement"));
        assertThat(statement.label()).isEqualTo("build the emergency fund first");
        assertThat(statement.facts()).containsExactly(
                new ChatMessage.Recommendation.Fact("Risk tolerance", "very low"),
                new ChatMessage.Recommendation.Fact("Emergency fund", "300.00 EUR"),
                new ChatMessage.Recommendation.Fact("Investment horizon", "15 years"));
        assertThat(statement.figures()).extracting(SourcedNumber::provId)
                .containsExactly("bbbbbbbbbbbbbbb1", "bbbbbbbbbbbbbbb2");
        assertThat(statement.risks()).isNotEmpty();
        assertThat(statement.costs()).isEmpty();
    }

    @Test
    void anInvestmentRecommendationWithoutCostsHasAnIncompleteStatement() {
        ChatMessage.Recommendation statement = Recommendations.statement(recommendation(null),
                Map.of("risk_tolerance", "medium"));
        assertThat(Recommendations.facts(statement, "savings_plan"))
                .containsEntry(CheckChain.STATEMENT_COMPLETE, false);
        assertThat(Recommendations.facts(statement, "leverage_or_loan"))
                .containsEntry(CheckChain.STATEMENT_COMPLETE, true)
                .containsEntry("category", CategorySelection.BUILD_EMERGENCY_FUND_FIRST);
        assertThat(Recommendations.facts(null, "savings_plan")).isNull();
    }

    @Test
    void aMissingRuleParameterMarksTheTurnWithoutARecommendation() {
        when(core.recommendation(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new de.financetool.knowledge.NoValidRuleParameterException(
                        "No valid version of short_horizon_years_max"));
        de.financetool.advisory.domain.TurnState.Tools tools = recommendations.run(TestContexts.context(),
                Map.of("risk_tolerance", "medium"), Map.of(), "savings_plan");
        assertThat(tools.ruleParameterError()).isTrue();
        assertThat(tools.recommendation()).isNull();
        assertThat(tools.statement()).isNull();
        assertThat(tools.results()).isEmpty();
    }

    @Test
    void theEmergencyFundChipLinksToTheOverviewOnlyWhenItIsTheAccountFigure() {
        accountFund("300.00");
        assertThat(recommendations.chips(TestContexts.context(), recommendation(null)))
                .extracting(SourcedNumber::provId).containsExactly("bbbbbbbbbbbbbbb1", "aaaaaaaaaaaaaaa3");
        accountFund("450.00");
        assertThat(recommendations.chips(TestContexts.context(), recommendation(null)))
                .extracting(SourcedNumber::provId).containsExactly("bbbbbbbbbbbbbbb1", "bbbbbbbbbbbbbbb2");
    }
}
