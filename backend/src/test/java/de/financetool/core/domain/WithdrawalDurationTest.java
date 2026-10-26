package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Reference values of Q&amp;A item 40 ({@code selftest.py}: 522/538) and the edges of the port. */
class WithdrawalDurationTest {

    static Map<String, BigDecimal> outputs(CoreComputation computation) {
        return computation.outputs().stream().collect(Collectors.toMap(Figure::outputKey, Figure::value));
    }

    @Test
    void item40Gives522EffectiveAnd538NominalWithReducedFinalWithdrawalsAndAssumptions() {
        CoreComputation c = WithdrawalDuration.compute(new BigDecimal("500000.00"), null, new BigDecimal("2000.00"),
                new BigDecimal("0.04"));
        Map<String, BigDecimal> out = outputs(c);

        assertThat(out.get(WithdrawalDuration.FULL_EFFECTIVE)).isEqualByComparingTo("522");
        assertThat(out.get(WithdrawalDuration.FULL_NOMINAL)).isEqualByComparingTo("538");
        assertThat(out.get(WithdrawalDuration.YEARS_EFFECTIVE)).isEqualTo(new BigDecimal("43.50"));
        assertThat(out.get(WithdrawalDuration.YEARS_NOMINAL)).isEqualTo(new BigDecimal("44.83"));
        assertThat(out.get(WithdrawalDuration.FINAL_EFFECTIVE)).isGreaterThan(BigDecimal.ZERO)
                .isLessThan(new BigDecimal("2000"));
        assertThat(out.get(WithdrawalDuration.FINAL_NOMINAL)).isGreaterThan(BigDecimal.ZERO)
                .isLessThan(new BigDecimal("2000"));
        assertThat(c.assumptions()).contains("Withdrawals at month end after that month's return",
                "Constant return, no costs, no taxes, no inflation adjustment");
        assertThat(c.outputs()).filteredOn(f -> f.outputKey().equals(WithdrawalDuration.ANNUAL_RATE))
                .singleElement().matches(f -> f.unit() == Unit.FRACTION);
    }

    @Test
    void theFinalWithdrawalsMatchTheExperiment() {
        // core.withdrawal_duration(500000, 2000, 0.04): final_withdrawal_effective / _nominal, q2 half-up.
        Map<String, BigDecimal> out = outputs(WithdrawalDuration.compute(new BigDecimal("500000"), null,
                new BigDecimal("2000"), new BigDecimal("0.04")));

        assertThat(out.get(WithdrawalDuration.FINAL_EFFECTIVE)).isEqualTo(new BigDecimal("24.66"));
        assertThat(out.get(WithdrawalDuration.FINAL_NOMINAL)).isEqualTo(new BigDecimal("847.26"));
    }

    @Test
    void aReturnCoveringTheWithdrawalNeverUsesThePortfolioUp() {
        CoreComputation c = WithdrawalDuration.compute(new BigDecimal("500000"), null, new BigDecimal("1000"),
                new BigDecimal("0.04"));

        assertThat(outputs(c).get(WithdrawalDuration.FULL_NOMINAL)).isEqualByComparingTo("1000000");
        assertThat(c.assumptions()).anyMatch(a -> a.contains("never used up"));
    }

    @Test
    void aTinyWithdrawalAtZeroReturnStopsAtThePerpetualMarker() {
        CoreComputation c = WithdrawalDuration.compute(new BigDecimal("1000000000"), null, new BigDecimal("0.01"),
                BigDecimal.ZERO);

        assertThat(outputs(c).get(WithdrawalDuration.FULL_EFFECTIVE)).isEqualByComparingTo("1000000");
    }

    @Test
    void aWithdrawalOfZeroIsRefused() {
        assertThatThrownBy(() -> WithdrawalDuration.compute(new BigDecimal("1000"), null, BigDecimal.ZERO,
                BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAccountPortfolioIsAnInputWithItsFigureKey() {
        SourcedNumber investments = new SourcedNumber("00000000000000b1", new BigDecimal("48210.00"), Unit.EUR,
                "Value of your investments today");
        CoreComputation c = WithdrawalDuration.compute(investments.value(), investments, new BigDecimal("500"),
                new BigDecimal("0.03"));

        assertThat(c.inputs().getFirst().provId()).isEqualTo("00000000000000b1");
        assertThat(c.keyInputs()).containsEntry(WithdrawalDuration.PORTFOLIO, new BigDecimal("48210.00"));
    }
}
