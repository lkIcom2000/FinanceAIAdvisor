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

    static final java.time.LocalDate AS_OF = java.time.LocalDate.of(2026, 10, 15);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', nullValues = "-", value = {
        "contract_switch | Should I switch to the new subsidised product? | 2027-01-01",
        "contract_switch | Do I get a Riester allowance next year? | 2027-01-01",
        "other | Bekomme ich nächstes Jahr noch die Riester-Zulage? | 2027-01-01",
        "subsidy_eligibility | What will the allowance be in 2027? | 2027-01-01",
        "contract_switch | Soll ich zum Altersvorsorgedepot wechseln? | 2027-01-01",
        "other | I retire in 2045, does Riester help? | -",
        "subsidy_eligibility | How does the subsidy work? | -",
        "subsidy_eligibility | I have had my contract since 2016. | -",
        "contract_switch | Should I switch my car insurance next year? | -",
        "other | My Riester contract runs until 2050. | -",
    })
    void aSubsidyQuestionAboutALaterYearNamesThatYear(String topic, String message, String expected) {
        assertThat(ToolPlan.laterSubsidyYear(nlu(topic, Map.of()), message, AS_OF))
                .isEqualTo(java.util.Optional.ofNullable(expected).map(java.time.LocalDate::parse));
    }

    static final String JONAS = "What if I save 150 EUR a month for 30 years, with the state subsidy?";

    static Stream<Arguments> whatIfs() {
        return Stream.of(
                Arguments.of("topic, amount and years", nlu("savings_projection",
                        Map.of("monthly_contribution", 150.0, "years", 30.0)), "q", true),
                Arguments.of("amount and years under another topic", nlu("subsidy_eligibility",
                        Map.of("monthly_contribution", 150.0, "years", 30.0)), JONAS, true),
                Arguments.of("topic without years", nlu("savings_projection",
                        Map.of("monthly_contribution", 150.0)), "q", false),
                Arguments.of("topic without an amount", nlu("savings_projection", Map.of("years", 30.0)), "q",
                        false),
                Arguments.of("years that are no whole number", nlu("savings_projection",
                        Map.of("monthly_contribution", 150.0, "years", 2.5)), "q", false),
                Arguments.of("more than sixty years", nlu("savings_projection",
                        Map.of("monthly_contribution", 150.0, "years", 61.0)), "q", false),
                Arguments.of("an amount of zero", nlu("savings_projection",
                        Map.of("monthly_contribution", 0.0, "years", 30.0)), "q", false),
                Arguments.of("withdrawal parameters keep the withdrawal plan", nlu("withdrawal_duration",
                        Map.of("monthly_contribution", 150.0, "years", 30.0)), "q", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatIfs")
    void plansAWhatIfWithAnAmountAndWholeYears(String name, NluExtraction extraction, String message,
            boolean planned) {
        Optional<ToolPlan.Planned> plan = ToolPlan.plan(extraction, message);

        assertThat(plan.isPresent()).isEqualTo(planned);
        plan.ifPresent(p -> assertThat(p.tool()).isEqualTo(ToolPlan.Tool.SAVINGS_PROJECTION));
    }

    static final SourcedNumber NET_INCOME = new SourcedNumber("00000000000000aa", new BigDecimal("2150.00"),
            Unit.EUR, "Average monthly net income");

    @Test
    void jonasWhatIfShowsAmountDurationAndTheSubsidyFromAccountData() {
        ToolPlan.Planned plan = ToolPlan.plan(nlu("savings_projection",
                Map.of("monthly_contribution", 150.0, "years", 30.0, "initial", 0.0)), JONAS).orElseThrow();
        SimulationSpec spec = ToolPlan.spec(plan, null, NET_INCOME);

        assertThat(spec.function()).isEqualTo("savingsProjection");
        assertThat(spec.parameters()).containsExactly(
                new SimulationSpec.Parameter("monthlyAmount", "Monthly amount", new BigDecimal("150.00"), Unit.EUR,
                        SimulationSpec.Source.QUESTION, null),
                new SimulationSpec.Parameter("years", "Duration", new BigDecimal("30"), Unit.YEARS,
                        SimulationSpec.Source.QUESTION, null),
                new SimulationSpec.Parameter("subsidy", "State subsidy (you receive a salary)", BigDecimal.ONE,
                        Unit.FLAG, SimulationSpec.Source.ACCOUNT_DATA, "00000000000000aa"));
        assertThat(ToolPlan.problems(spec)).isEmpty();
    }

    @Test
    void aWhatIfWithoutSubsidyWordsHasNoSubsidyRowAndAStatedStartIsARow() {
        ToolPlan.Planned plan = ToolPlan.plan(nlu("savings_projection",
                Map.of("monthly_contribution", 200.0, "years", 10.0, "initial", 5000.0)),
                "What if I start with 5,000 EUR and save 200 EUR a month for 10 years?").orElseThrow();
        SimulationSpec spec = ToolPlan.spec(plan, null, null);

        assertThat(spec.parameters()).extracting(SimulationSpec.Parameter::key)
                .containsExactly("monthlyAmount", "years", "initial");
        assertThat(spec.parameter("initial").value()).isEqualByComparingTo("5000");
    }

    @Test
    void withoutSalaryIncomeTheSubsidyRowSaysNo() {
        ToolPlan.Planned plan = ToolPlan.plan(nlu("savings_projection",
                Map.of("monthly_contribution", 150.0, "years", 30.0)), JONAS).orElseThrow();
        SourcedNumber none = new SourcedNumber("00000000000000ab", new BigDecimal("0.00"), Unit.EUR, "income");

        SimulationSpec.Parameter subsidy = ToolPlan.spec(plan, null, none).parameter("subsidy");

        assertThat(subsidy.value()).isEqualByComparingTo("0");
        assertThat(subsidy.label()).contains("not eligible");
    }

    @Test
    void thePlanAndTheCoreAgreeOnTheLongestHorizon() {
        assertThat(ToolPlan.MAX_YEARS).isEqualTo(de.financetool.core.domain.WhatIfProjection.MAX_YEARS);
    }

    @Test
    void whatIfProblemsNameValuesOutOfBoundsAndAChangedSubsidy() {
        ToolPlan.Planned plan = ToolPlan.plan(nlu("savings_projection",
                Map.of("monthly_contribution", 150.0, "years", 30.0)), JONAS).orElseThrow();
        SimulationSpec spec = ToolPlan.spec(plan, null, NET_INCOME);
        SimulationSpec changed = new SimulationSpec(spec.function(), List.of(
                spec.parameter("monthlyAmount").changedTo(BigDecimal.ZERO),
                spec.parameter("years").changedTo(new BigDecimal("2.5")),
                spec.parameter("subsidy").changedTo(BigDecimal.ZERO)));

        assertThat(ToolPlan.problems(changed)).containsExactly(
                "The monthly amount must be more than 0 and at most 1,000,000,000 EUR.",
                "The duration must be a whole number of years from 1 to 60.",
                "The state subsidy can't be changed here. Please ask a new question instead.");
    }

    @Test
    void everyTopicWithARuleMapsToItsPersonalFigures() {
        assertThat(ToolPlan.PersonalFigures.forTopic("liquidity_management"))
                .contains(ToolPlan.PersonalFigures.EMERGENCY_FUND);
        assertThat(ToolPlan.PersonalFigures.forTopic("retirement_strategy"))
                .contains(ToolPlan.PersonalFigures.GOAL_PROJECTION);
        assertThat(ToolPlan.PersonalFigures.forTopic("protection_needs")).contains(ToolPlan.PersonalFigures.COVERAGE);
        assertThat(ToolPlan.PersonalFigures.forTopic("protection_duplicate"))
                .contains(ToolPlan.PersonalFigures.COVERAGE);
        assertThat(ToolPlan.PersonalFigures.forTopic("diversification")).isEmpty();
        assertThat(ToolPlan.PersonalFigures.forTopic(null)).isEmpty();
    }

    static NluExtraction explanation(String subject, String topic) {
        return new NluExtraction("general_explanation", subject, topic, List.of(), List.of(), false, false, Map.of(),
                Map.of(), "");
    }

    @Test
    void onlyAnInformTurnAboutTheUserRunsPersonalFigures() {
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("self", "liquidity_management")))
                .contains(ToolPlan.PersonalFigures.EMERGENCY_FUND);
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("generic", "liquidity_management")))
                .isEmpty();
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("specific_client",
                "liquidity_management"))).isEmpty();
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("self", "diversification"))).isEmpty();
        // a follow-up about nobody on an answer with Core figures; never about another person
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("generic", "liquidity_management"), true))
                .contains(ToolPlan.PersonalFigures.EMERGENCY_FUND);
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("specific_client",
                "liquidity_management"), true)).isEmpty();
        assertThat(ToolPlan.personalFigures(MessageClass.INFORM, explanation("generic", "diversification"), true))
                .isEmpty();
        assertThat(ToolPlan.personalFigures(MessageClass.COMPUTE, explanation("generic", "liquidity_management"),
                true)).isEmpty();
        for (MessageClass other : MessageClass.values()) {
            if (other != MessageClass.INFORM) {
                assertThat(ToolPlan.personalFigures(other, explanation("self", "liquidity_management"))).as(
                        other.name()).isEmpty();
            }
        }
    }
}
