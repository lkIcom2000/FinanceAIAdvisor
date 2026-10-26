package de.financetool.advisory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The tool plan as a table: {@code _tool_plan} of the experiment, plus the account-data portfolio. */
class ToolPlanTest {

    static NluExtraction nlu(String topic, Map<String, Double> params) {
        return new NluExtraction("computation", "self", topic, List.of(), List.of(), false, false, Map.of(), params,
                "");
    }

    static final Map<String, Double> ALL = Map.of("portfolio", 500000.0, "monthly_withdrawal", 2000.0,
            "annual_rate", 0.04);

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("topic and all three", nlu("withdrawal_duration", ALL), "q", true, false),
                Arguments.of("portfolio and withdrawal without the topic", nlu("other", ALL), "q", true, false),
                Arguments.of("topic without a rate", nlu("withdrawal_duration",
                        Map.of("portfolio", 500000.0, "monthly_withdrawal", 2000.0)), "q", false, false),
                Arguments.of("topic without a withdrawal", nlu("withdrawal_duration",
                        Map.of("portfolio", 500000.0, "annual_rate", 0.04)), "q", false, false),
                Arguments.of("another topic with only a withdrawal and a rate", nlu("savings_projection",
                        Map.of("monthly_withdrawal", 2000.0, "annual_rate", 0.04)), "my savings", false, false),
                Arguments.of("no parameters", nlu("withdrawal_duration", Map.of()), "q", false, false),
                Arguments.of("own savings without an amount", nlu("withdrawal_duration",
                        Map.of("monthly_withdrawal", 2000.0, "annual_rate", 0.04)),
                        "How long will my savings last?", true, true),
                Arguments.of("own savings with a portfolio of zero", nlu("withdrawal_duration",
                        Map.of("portfolio", 0.0, "monthly_withdrawal", 2000.0, "annual_rate", 0.04)),
                        "How long will my savings last?", true, true),
                Arguments.of("someone else's savings without an amount", nlu("withdrawal_duration",
                        Map.of("monthly_withdrawal", 2000.0, "annual_rate", 0.04)),
                        "How long does a portfolio last?", false, false),
                Arguments.of("a withdrawal of zero", nlu("withdrawal_duration", Map.of("portfolio", 500000.0,
                        "monthly_withdrawal", 0.0, "annual_rate", 0.04)), "q", false, false),
                Arguments.of("a return that is not a number", nlu("withdrawal_duration", Map.of("portfolio",
                        500000.0, "monthly_withdrawal", 2000.0, "annual_rate", Double.NaN)), "q", false, false),
                Arguments.of("a return above 20 %", nlu("withdrawal_duration", Map.of("portfolio", 500000.0,
                        "monthly_withdrawal", 2000.0, "annual_rate", 0.25)), "q", false, false),
                Arguments.of("a negative return within bounds", nlu("withdrawal_duration", Map.of("portfolio",
                        500000.0, "monthly_withdrawal", 2000.0, "annual_rate", -0.02)), "q", true, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void plansTheWithdrawalDurationOnlyWithEveryParameter(String name, NluExtraction extraction, String message,
            boolean planned, boolean fromAccount) {
        Optional<ToolPlan.Planned> plan = ToolPlan.plan(extraction, message);

        assertThat(plan.isPresent()).isEqualTo(planned);
        plan.ifPresent(p -> {
            assertThat(p.tool()).isEqualTo(ToolPlan.Tool.WITHDRAWAL_DURATION);
            assertThat(p.portfolioFromAccount()).isEqualTo(fromAccount);
        });
    }

    @Test
    void theSpecShowsEachParameterWithUnitAndSourceInCentsAndAsAShortRate() {
        SimulationSpec spec = ToolPlan.spec(ToolPlan.plan(nlu("withdrawal_duration", ALL), "q").orElseThrow(), null);

        assertThat(spec.function()).isEqualTo("withdrawalDuration");
        assertThat(spec.parameters()).containsExactly(
                new SimulationSpec.Parameter("portfolio", "Starting portfolio", new BigDecimal("500000.00"), Unit.EUR,
                        SimulationSpec.Source.QUESTION, null),
                new SimulationSpec.Parameter("monthlyWithdrawal", "Monthly withdrawal", new BigDecimal("2000.00"),
                        Unit.EUR, SimulationSpec.Source.QUESTION, null),
                new SimulationSpec.Parameter("annualRate", "Assumed annual return", new BigDecimal("0.04"),
                        Unit.FRACTION, SimulationSpec.Source.QUESTION, null));
    }

    @Test
    void anAccountPortfolioCarriesItsFigureKey() {
        SourcedNumber investments = new SourcedNumber("00000000000000b1", new BigDecimal("48210.00"), Unit.EUR,
                "Value of your investments today");
        SimulationSpec spec = ToolPlan.spec(ToolPlan.plan(nlu("withdrawal_duration",
                Map.of("monthly_withdrawal", 500.0, "annual_rate", 0.03)), "my portfolio").orElseThrow(),
                investments);

        assertThat(spec.parameter("portfolio").source()).isEqualTo(SimulationSpec.Source.ACCOUNT_DATA);
        assertThat(spec.parameter("portfolio").provId()).isEqualTo("00000000000000b1");
    }

    @Test
    void problemsNameEachValueOutOfBounds() {
        SimulationSpec spec = new SimulationSpec("withdrawalDuration", List.of(
                new SimulationSpec.Parameter("portfolio", "p", BigDecimal.ZERO, Unit.EUR,
                        SimulationSpec.Source.CHANGED, null),
                new SimulationSpec.Parameter("monthlyWithdrawal", "w", new BigDecimal("2000000000"), Unit.EUR,
                        SimulationSpec.Source.CHANGED, null),
                new SimulationSpec.Parameter("annualRate", "r", new BigDecimal("0.21"), Unit.FRACTION,
                        SimulationSpec.Source.CHANGED, null)));

        assertThat(ToolPlan.problems(spec)).hasSize(3);
        assertThat(ToolPlan.problems(new SimulationSpec("somethingElse", List.of()))).hasSize(1);
    }
}
