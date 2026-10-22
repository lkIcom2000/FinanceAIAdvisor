package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Story 2.4: the seeded goal projection is deterministic, a range with assumptions, and can bootstrap a series. */
class MonteCarloProjectionTest {

    static final LocalDate FROM = LocalDate.of(2020, 1, 1);
    static final RuleParameterValue MEAN = value(MonteCarloProjection.MEAN_RETURN, "0.06");
    static final RuleParameterValue VOLATILITY = value(MonteCarloProjection.VOLATILITY, "0.15");
    static final RuleParameterValue TER = value(MonteCarloProjection.TER, "0.002");
    static final RuleParameterValue INFLATION = value(MonteCarloProjection.INFLATION, "0.02");
    static final RuleParameterValue HORIZON = value(MonteCarloProjection.HORIZON, "30");
    static final SourcedNumber INITIAL = new SourcedNumber("aaaaaaaaaaaaaaaa", new BigDecimal("2004.76"), Unit.EUR,
            "Value of your investments today");
    static final SourcedNumber MONTHLY = new SourcedNumber("bbbbbbbbbbbbbbbb", new BigDecimal("100.00"), Unit.EUR,
            "Monthly savings plan contributions");
    static final MonteCarloProjection.ReturnModel NORMAL = new MonteCarloProjection.Parametric(MEAN, VOLATILITY);

    static RuleParameterValue value(String key, String value) {
        return new RuleParameterValue(key, new BigDecimal(value), "1", FROM, null);
    }

    static MonteCarloProjection project(long seed, MonteCarloProjection.ReturnModel model) {
        return MonteCarloProjection.project(INITIAL, MONTHLY, HORIZON, MonteCarloProjection.DEFAULT_PATHS, seed,
                model, TER, INFLATION);
    }

    static BigDecimal output(MonteCarloProjection projection, String key) {
        return projection.computation().outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst()
                .orElseThrow().value();
    }

    @Test
    void theSameInputsAndSeedGiveTheSameResult() {
        MonteCarloProjection first = project(20261020L, NORMAL);
        MonteCarloProjection second = project(20261020L, NORMAL);

        assertThat(second.points()).isEqualTo(first.points());
        assertThat(second.computation()).isEqualTo(first.computation());
    }

    @Test
    void anotherSeedGivesOtherPercentiles() {
        assertThat(output(project(1L, NORMAL), MonteCarloProjection.OUTPUT_P50))
                .isNotEqualByComparingTo(output(project(2L, NORMAL), MonteCarloProjection.OUTPUT_P50));
    }

    @Test
    void theResultIsAnOrderedRangeWithYearlyPointsPaidInAndTodaysMoney() {
        MonteCarloProjection projection = project(20261020L, NORMAL);

        assertThat(projection.points()).extracting(MonteCarloProjection.Point::year)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 30).boxed().toList());
        assertThat(projection.points()).allSatisfy(p -> {
            assertThat(p.p10()).isLessThan(p.p50());
            assertThat(p.p50()).isLessThan(p.p90());
            assertThat(p.p50().scale()).isEqualTo(2);
        });
        MonteCarloProjection.Point end = projection.points().getLast();
        assertThat(output(projection, MonteCarloProjection.OUTPUT_P10)).isEqualTo(end.p10());
        assertThat(output(projection, MonteCarloProjection.OUTPUT_P50)).isEqualTo(end.p50());
        assertThat(output(projection, MonteCarloProjection.OUTPUT_P90)).isEqualTo(end.p90());
        assertThat(output(projection, MonteCarloProjection.OUTPUT_PAID_IN)).isEqualTo("38004.76");
        // 6 % p.a. on 100/month for 30 years lands well above what was paid in; today's money is below the nominal.
        assertThat(end.p50()).isGreaterThan(new BigDecimal("38004.76"));
        assertThat(output(projection, MonteCarloProjection.OUTPUT_P50_REAL)).isLessThan(end.p50());

        assertThat(projection.assumptions()).hasSize(5);
        assertThat(projection.assumptions().getFirst()).startsWith("Returns: normally distributed monthly returns")
                .contains("6 %").contains("15 %");
        assertThat(projection.assumptions()).anySatisfy(a -> assertThat(a).contains("0.2 %"))
                .anySatisfy(a -> assertThat(a).startsWith("Inflation: 2 %"))
                .anySatisfy(a -> assertThat(a).startsWith("Horizon: 30 years"));
        assertThat(projection.computation().ruleParamVersions()).containsOnlyKeys(MonteCarloProjection.MEAN_RETURN,
                MonteCarloProjection.VOLATILITY, MonteCarloProjection.TER, MonteCarloProjection.INFLATION,
                MonteCarloProjection.HORIZON);
        assertThat(projection.computation().inputs()).filteredOn(i -> i.provId() != null)
                .extracting(FigureInput::provId).containsExactly(INITIAL.provId(), MONTHLY.provId());
    }

    @Test
    void aHistoricalSeriesIsBootstrappedAndNamedInTheAssumptions() {
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            returns.add(new BigDecimal(i % 3 == 0 ? "-0.03" : "0.012"));
        }
        MonteCarloProjection.Bootstrap bootstrap = new MonteCarloProjection.Bootstrap("msci-world.csv", returns);
        MonteCarloProjection first = project(7L, bootstrap);

        assertThat(project(7L, bootstrap).points()).isEqualTo(first.points());
        assertThat(first.assumptions().getFirst())
                .isEqualTo("Returns: each month's return is drawn at random from 120 historical monthly index"
                        + " returns (msci-world.csv).");
        assertThat(first.computation().ruleParamVersions()).doesNotContainKeys(MonteCarloProjection.MEAN_RETURN,
                MonteCarloProjection.VOLATILITY);
        MonteCarloProjection.Point end = first.points().getLast();
        assertThat(end.p10()).isLessThan(end.p50());
        assertThat(end.p50()).isLessThan(end.p90());
        assertThat(first.computation().keyInputs()).isNotEqualTo(project(7L, NORMAL).computation().keyInputs());
    }

    @Test
    void aShortSeriesCannotBeBootstrapped() {
        List<BigDecimal> returns = Collections.nCopies(24, new BigDecimal("0.01"));
        assertThatThrownBy(() -> new MonteCarloProjection.Bootstrap("short.csv", returns))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aProjectionWithoutAssumptionsOrPercentilesFailsValidation() {
        MonteCarloProjection valid = project(1L, NORMAL);
        assertThatThrownBy(() -> new MonteCarloProjection(valid.points(), List.of(), valid.computation()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("assumptions");
        assertThatThrownBy(() -> new MonteCarloProjection(List.of(), valid.assumptions(), valid.computation()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("range");
        CoreComputation withoutPercentiles = new CoreComputation(MonteCarloProjection.FUNCTION, "goal projection",
                "basis", Map.of(), List.of(), List.of(new Figure("paidIn", "Paid in", BigDecimal.ONE, Unit.EUR)),
                valid.assumptions(), Map.of());
        assertThatThrownBy(() -> new MonteCarloProjection(valid.points(), valid.assumptions(), withoutPercentiles))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("p10, p50 and p90");
    }

    @Test
    void aWrongRuleParameterIsRefused() {
        assertThatThrownBy(() -> MonteCarloProjection.project(INITIAL, MONTHLY, TER,
                MonteCarloProjection.DEFAULT_PATHS, 1L, NORMAL, TER, INFLATION))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(MonteCarloProjection.HORIZON);
    }

    @Test
    void percentilesInterpolateLinearly() {
        double[] sorted = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
        assertThat(MonteCarloProjection.percentile(sorted, 0.10)).isEqualTo(2.0);
        assertThat(MonteCarloProjection.percentile(sorted, 0.50)).isEqualTo(6.0);
        assertThat(MonteCarloProjection.percentile(new double[] {0, 10}, 0.25)).isEqualTo(2.5);
    }
}
