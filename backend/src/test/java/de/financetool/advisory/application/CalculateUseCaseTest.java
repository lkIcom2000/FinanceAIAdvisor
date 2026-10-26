package de.financetool.advisory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.SimulationSpec;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** "Change values": changed values are tagged, normalised and bounded before anything is calculated. */
class CalculateUseCaseTest {

    final SimulationSpec spec = TurnOrchestratorTest.item40Spec();

    @Test
    void aChangedValueIsTaggedAndAnUnchangedOneKeepsItsSource() {
        SimulationSpec changed = CalculateUseCase.changedSpec(spec, Map.of("monthlyWithdrawal", new BigDecimal("2500"),
                "portfolio", new BigDecimal("500000"), "annualRate", new BigDecimal("0.0300")));

        assertThat(changed.parameter("monthlyWithdrawal").value()).isEqualTo(new BigDecimal("2500.00"));
        assertThat(changed.parameter("monthlyWithdrawal").source()).isEqualTo(SimulationSpec.Source.CHANGED);
        assertThat(changed.parameter("portfolio").source()).as("same value, same source")
                .isEqualTo(SimulationSpec.Source.QUESTION);
        assertThat(changed.parameter("annualRate").value()).isEqualTo(new BigDecimal("0.03"));
    }

    @Test
    void noChangesCalculateAsProposed() {
        assertThat(CalculateUseCase.changedSpec(spec, Map.of())).isEqualTo(spec);
    }

    @Test
    void anUnknownKeyOrAValueOutOfBoundsIsRefused() {
        assertThatThrownBy(() -> CalculateUseCase.changedSpec(spec, Map.of("years", BigDecimal.TEN)))
                .isInstanceOf(CalculateUseCase.InvalidValuesException.class);
        assertThatThrownBy(() -> CalculateUseCase.changedSpec(spec, Map.of("annualRate", new BigDecimal("4"))))
                .isInstanceOf(CalculateUseCase.InvalidValuesException.class)
                .hasMessageContaining("between -20 % and 20 %");
    }
}
