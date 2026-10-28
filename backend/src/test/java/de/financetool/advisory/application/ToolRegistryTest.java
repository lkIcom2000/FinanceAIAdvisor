package de.financetool.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.advisory.domain.ToolPlan;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.core.domain.WealthFigures;
import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.shared.RequestContext;
import de.financetool.testing.TestContexts;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Story 13.4: the Core calls behind each personal-figures rule. */
class ToolRegistryTest {

    final CoreGateway core = mock(CoreGateway.class);
    final ToolRegistry registry = new ToolRegistry(core);
    final RequestContext context = TestContexts.context();

    static CoreResult result(String function) {
        return new CoreResult(function, List.of(), List.of(), List.of(), Map.of(), List.of());
    }

    @Test
    void theEmergencyFundRuleReturnsTheEmergencyFundResult() {
        CoreResult fund = result("emergencyFundTarget");
        when(core.profileFigures(context, EvidenceOrigin.TURN))
                .thenReturn(new ProfileFigures(result("monthlyNetIncome"), fund));

        assertThat(registry.personalFigures(context, ToolPlan.PersonalFigures.EMERGENCY_FUND)).containsExactly(fund);
    }

    @Test
    void theGoalProjectionRuleReturnsTheTotalsAndTheProjection() {
        CoreResult totals = result("investmentTotals");
        CoreResult projection = result("monteCarloProjection");
        WealthFigures wealth = mock(WealthFigures.class);
        when(wealth.totals()).thenReturn(totals);
        when(wealth.projection()).thenReturn(projection);
        when(core.wealth(context, EvidenceOrigin.TURN)).thenReturn(wealth);

        assertThat(registry.personalFigures(context, ToolPlan.PersonalFigures.GOAL_PROJECTION))
                .containsExactly(totals, projection);
    }

    @Test
    void theGoalProjectionRuleWithoutAProjectionReturnsNothing() {
        when(core.wealth(context, EvidenceOrigin.TURN))
                .thenReturn(new WealthFigures(List.of(), List.of(), null, null, List.of(), List.of()));

        assertThat(registry.personalFigures(context, ToolPlan.PersonalFigures.GOAL_PROJECTION)).isEmpty();
    }

    @Test
    void theCoverageRuleReturnsTheDuplicateAndTheMissingCover() {
        CoreResult duplicates = result("duplicateCoverage");
        CoreResult missing = result("missingEssentialCover");
        when(core.insurance(context, EvidenceOrigin.TURN))
                .thenReturn(new InsuranceFigures(List.of(), duplicates, List.of(), missing, List.of()));

        assertThat(registry.personalFigures(context, ToolPlan.PersonalFigures.COVERAGE))
                .containsExactly(duplicates, missing);
    }

    @Test
    void aMissingRuleParameterIsThrownToTheStage() {
        when(core.profileFigures(context, EvidenceOrigin.TURN))
                .thenThrow(new NoValidRuleParameterException("No version"));

        assertThatThrownBy(() -> registry.personalFigures(context, ToolPlan.PersonalFigures.EMERGENCY_FUND))
                .isInstanceOf(NoValidRuleParameterException.class);
    }
}
