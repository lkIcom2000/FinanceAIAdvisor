package de.financetool.core.domain;

import static de.financetool.core.domain.MonteCarloProjectionTest.INFLATION;
import static de.financetool.core.domain.MonteCarloProjectionTest.NORMAL;
import static de.financetool.core.domain.MonteCarloProjectionTest.TER;
import static de.financetool.core.domain.MonteCarloProjectionTest.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Story 7.1: the seeded what-if projection, with and without the Riester basic allowance. */
class WhatIfProjectionTest {

    static final long SEED = 20261026L;
    static final BigDecimal MONTHLY = new BigDecimal("150.00");
    static final SourcedNumber NET_INCOME = new SourcedNumber("cccccccccccccccc", new BigDecimal("2150.00"), Unit.EUR,
            "Average monthly net income");
    static final RuleParameterValue BASIC = value(RiesterSubsidy.BASIC, "175");
    static final RuleParameterValue FLOOR = value(RiesterSubsidy.FLOOR, "60");
    static final WhatIfProjection.Subsidy ELIGIBLE = new WhatIfProjection.Subsidy(true, NET_INCOME, BASIC, FLOOR);

    static WhatIfProjection project(BigDecimal monthly, WhatIfProjection.Subsidy subsidy) {
        return WhatIfProjection.project(monthly, 30, BigDecimal.ZERO, subsidy, MonteCarloProjection.DEFAULT_PATHS,
                SEED, NORMAL, TER, INFLATION);
    }

    static BigDecimal output(WhatIfProjection projection, String key) {
        return projection.computation().outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst()
                .orElseThrow().value();
    }

    static boolean has(WhatIfProjection projection, String key) {
        return projection.computation().outputs().stream().anyMatch(f -> f.outputKey().equals(key));
    }

    @Test
    void theSameSpecAndSeedGiveTheSameResult() {
        WhatIfProjection first = project(MONTHLY, ELIGIBLE);
        WhatIfProjection second = project(MONTHLY, ELIGIBLE);

        assertThat(second.points()).isEqualTo(first.points()).hasSize(30);
        assertThat(second.computation()).isEqualTo(first.computation());
    }

    @Test
    void theAllowanceRaisesEveryPercentileAndIsListed() {
        WhatIfProjection with = project(MONTHLY, ELIGIBLE);
        WhatIfProjection without = project(MONTHLY, null);

        for (String key : new String[] {WhatIfProjection.OUTPUT_P10, WhatIfProjection.OUTPUT_P50,
                WhatIfProjection.OUTPUT_P90}) {
            assertThat(output(with, key)).isGreaterThan(output(without, key));
        }
        assertThat(output(with, WhatIfProjection.OUTPUT_ALLOWANCES)).isEqualByComparingTo("5250.00");
        assertThat(output(with, WhatIfProjection.OUTPUT_PAID_IN)).isEqualByComparingTo("54000.00");
        assertThat(has(without, WhatIfProjection.OUTPUT_ALLOWANCES)).isFalse();
        assertThat(with.computation().ruleParamVersions()).containsKeys(RiesterSubsidy.BASIC, RiesterSubsidy.FLOOR);
        assertThat(with.assumptions()).anyMatch(a -> a.contains("riester_basic_allowance version 1"));
        assertThat(without.assumptions()).anyMatch(a -> a.contains("the question did not ask"));
    }

    @Test
    void belowTheMinimumOwnPaymentOrWithoutEligibilityNoAllowanceIsCredited() {
        WhatIfProjection small = project(new BigDecimal("4.00"), ELIGIBLE);
        WhatIfProjection notEligible = project(MONTHLY,
                new WhatIfProjection.Subsidy(false, NET_INCOME, null, null));

        assertThat(has(small, WhatIfProjection.OUTPUT_ALLOWANCES)).isFalse();
        assertThat(small.assumptions()).anyMatch(a -> a.contains("stays below the minimum"));
        assertThat(has(notEligible, WhatIfProjection.OUTPUT_ALLOWANCES)).isFalse();
        assertThat(output(notEligible, WhatIfProjection.OUTPUT_P50))
                .isEqualByComparingTo(output(project(MONTHLY, null), WhatIfProjection.OUTPUT_P50));
        assertThat(notEligible.assumptions()).anyMatch(a -> a.contains("no salary income"));
    }

    @Test
    void exactlyTheMinimumOwnPaymentEarnsTheAllowance() {
        assertThat(output(project(new BigDecimal("5.00"), ELIGIBLE), WhatIfProjection.OUTPUT_ALLOWANCES))
                .isEqualByComparingTo("5250.00");
    }

    @Test
    void aStartAmountCountsAsPaidIn() {
        WhatIfProjection withStart = WhatIfProjection.project(MONTHLY, 30, new BigDecimal("1000.00"), null,
                MonteCarloProjection.DEFAULT_PATHS, SEED, NORMAL, TER, INFLATION);

        assertThat(output(withStart, WhatIfProjection.OUTPUT_PAID_IN)).isEqualByComparingTo("55000.00");
        assertThat(output(withStart, WhatIfProjection.OUTPUT_P50))
                .isGreaterThan(output(project(MONTHLY, null), WhatIfProjection.OUTPUT_P50));
    }

    @Test
    void theAssumptionsNameReturnModelCostsInflationHorizonAndSubsidy() {
        assertThat(project(MONTHLY, ELIGIBLE).assumptions())
                .anyMatch(a -> a.startsWith("Returns:"))
                .anyMatch(a -> a.startsWith("Costs:"))
                .anyMatch(a -> a.startsWith("Inflation:"))
                .anyMatch(a -> a.startsWith("Horizon: 30 years"))
                .anyMatch(a -> a.startsWith("State subsidy: the basic allowance"));
    }

    @Test
    void refusesAnAmountOfZeroAndAHorizonOutsideOneToSixtyYears() {
        assertThatThrownBy(() -> project(BigDecimal.ZERO, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WhatIfProjection.project(MONTHLY, 61, BigDecimal.ZERO, null, 100, SEED, NORMAL, TER,
                INFLATION)).isInstanceOf(IllegalArgumentException.class);
    }
}
