package de.financetool.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void roundsToScaleTwoHalfUp() {
        assertThat(Money.eur("1234.505").amountString()).isEqualTo("1234.51");
        assertThat(Money.eur("1234.5").amountString()).isEqualTo("1234.50");
        assertThat(Money.eur(new BigDecimal("-0.005")).amountString()).isEqualTo("-0.01");
    }

    @Test
    void equalAmountsAreEqualRegardlessOfInputScale() {
        assertThat(Money.eur("10")).isEqualTo(Money.eur("10.00"));
    }

    @Test
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "eur")).isInstanceOf(IllegalArgumentException.class);
    }
}
