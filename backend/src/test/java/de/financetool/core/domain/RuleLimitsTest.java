package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Story 9.1: the limits the Core suggests for the two rule templates. */
class RuleLimitsTest {

    static SourcedNumber eur(String value) {
        return new SourcedNumber("0123456789abcdef", new BigDecimal(value), Unit.EUR, "figure");
    }

    static BigDecimal output(CoreComputation c, String key) {
        return c.outputs().stream().filter(f -> f.outputKey().equals(key)).findFirst().orElseThrow().value();
    }

    @Test
    void theCushionCoversTheLowestBalanceRoundedUpAndKeepsTheEmergencyFundTarget() {
        CoreComputation c = RuleLimits.of(eur("-190.00"), eur("2040.50"), eur("237.00"));

        assertThat(output(c, RuleLimits.OUTPUT_CUSHION_MAX_PER_TRANSFER)).isEqualByComparingTo("200");
        assertThat(output(c, RuleLimits.OUTPUT_CUSHION_MAX_PER_MONTH)).isEqualByComparingTo("400");
        assertThat(output(c, RuleLimits.OUTPUT_CUSHION_FLOOR)).isEqualByComparingTo("2040.50");
        assertThat(output(c, RuleLimits.OUTPUT_TOP_UP_MAX_PER_TRANSFER)).isEqualByComparingTo("300");
        assertThat(output(c, RuleLimits.OUTPUT_TOP_UP_MAX_PER_MONTH)).isEqualByComparingTo("300");
        assertThat(output(c, RuleLimits.OUTPUT_TOP_UP_FLOOR)).isEqualByComparingTo("0");
        assertThat(c.function()).isEqualTo(RuleLimits.FUNCTION);
    }

    @Test
    void amountsAreAtLeastOneHundredEuro() {
        CoreComputation c = RuleLimits.of(eur("350.00"), eur("0"), eur("0"));

        assertThat(output(c, RuleLimits.OUTPUT_CUSHION_MAX_PER_TRANSFER)).isEqualByComparingTo("100");
        assertThat(output(c, RuleLimits.OUTPUT_TOP_UP_MAX_PER_TRANSFER)).isEqualByComparingTo("100");
        assertThat(RuleLimits.roundUp(new BigDecimal("200.00"))).isEqualByComparingTo("200");
        assertThat(RuleLimits.roundUp(new BigDecimal("200.01"))).isEqualByComparingTo("300");
    }
}
