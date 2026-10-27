package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Story 8.2: the top-up is the gap to the target, at most what an average month leaves over, never negative. */
class EmergencyFundTopUpTest {

    static SourcedNumber eur(String value) {
        return new SourcedNumber("0123456789abcdef", new BigDecimal(value), Unit.EUR, "figure");
    }

    static BigDecimal topUp(String current, String target, String income, String expenses) {
        return EmergencyFundTopUp.of(eur(current), eur(target), eur(income), eur(expenses)).outputs().getFirst()
                .value();
    }

    @Test
    void theSurplusCapsALargeGap() {
        assertThat(topUp("1000", "6000", "2500", "2100")).isEqualByComparingTo("400.00");
    }

    @Test
    void aSmallGapCapsTheSurplus() {
        assertThat(topUp("5900", "6000", "2500", "2100")).isEqualByComparingTo("100.00");
    }

    @Test
    void withoutSurplusOrGapTheTopUpIsZero() {
        assertThat(topUp("1000", "6000", "2000", "2100")).isEqualByComparingTo("0.00");
        assertThat(topUp("7000", "6000", "2500", "2100")).isEqualByComparingTo("0.00");
    }
}
