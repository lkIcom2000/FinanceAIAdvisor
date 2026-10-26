package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ex-ante cost calculation, ported from {@code cost_comparison} of {@code experiments/finlab/core.py}. */
class CostCalculationTest {

    static RuleParameterValue parameter(String key, String value) {
        return new RuleParameterValue(key, new BigDecimal(value), "1", LocalDate.of(2020, 1, 1), null);
    }

    static BigDecimal output(CoreComputation computation, String key) {
        return computation.outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst()
                .map(Figure::value).orElseThrow();
    }

    @Test
    void comparesTheEtfWithAnActiveFundOverTenYears() {
        CoreComputation costs = CostCalculation.of(new BigDecimal("4800"), 10,
                parameter(CostCalculation.ETF_TER, "0.002"), parameter(CostCalculation.ACTIVE_TER, "0.015"),
                parameter(CostCalculation.FRONT_LOAD, "0.05"));
        // experiment: etf 4800 * 0.002 * 10, active 4800 * 0.05 + 4800 * 0.015 * 10
        assertThat(output(costs, CostCalculation.OUTPUT_ETF_TOTAL)).isEqualByComparingTo("96.00");
        assertThat(output(costs, CostCalculation.OUTPUT_ACTIVE_TOTAL)).isEqualByComparingTo("960.00");
        assertThat(output(costs, CostCalculation.OUTPUT_DIFFERENCE)).isEqualByComparingTo("864.00");
        assertThat(output(costs, CostCalculation.OUTPUT_FRONT_LOAD_AMOUNT)).isEqualByComparingTo("240.00");
        assertThat(costs.ruleParamVersions()).containsOnlyKeys(CostCalculation.ETF_TER, CostCalculation.ACTIVE_TER,
                CostCalculation.FRONT_LOAD);
    }

    @Test
    void theAmountIsTheProfilesAmountElseTwelveMonthlyAmountsElseTheDefault() {
        assertThat(CostCalculation.amount(Map.of("amount", 5000.0, "requested_monthly_amount", 400)))
                .isEqualByComparingTo("5000.00");
        assertThat(CostCalculation.amount(Map.of("requested_monthly_amount", 400))).isEqualByComparingTo("4800.00");
        assertThat(CostCalculation.amount(Map.of("amount", 0))).isEqualByComparingTo("10000.00");
        assertThat(CostCalculation.amount(Map.of())).isEqualByComparingTo("10000.00");
    }
}
