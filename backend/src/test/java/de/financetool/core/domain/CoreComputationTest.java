package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreComputationTest {

    @Test
    void duplicateOutputKeysAreRejected() {
        List<Figure> outputs = List.of(new Figure("balance", "A", BigDecimal.ONE, Unit.EUR),
                new Figure("balance", "B", BigDecimal.TEN, Unit.EUR));

        assertThatThrownBy(() -> new CoreComputation("f", "plain", "basis", Map.of(), List.of(), outputs, List.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }
}
