package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountBalanceTest {

    static final LocalDate OPENED = LocalDate.of(2025, 10, 1);
    static final List<AccountBalance.Booking> BOOKINGS = List.of(
            new AccountBalance.Booking(LocalDate.of(2025, 10, 1), new BigDecimal("2400.00")),
            new AccountBalance.Booking(LocalDate.of(2026, 10, 15), new BigDecimal("-120.55")),
            new AccountBalance.Booking(LocalDate.of(2026, 10, 16), new BigDecimal("-999.00")));

    @Test
    void theBalanceIsOpeningPlusBookingsUpToTheEndOfAsOf() {
        CoreComputation result = AccountBalance.of(OPENED, new BigDecimal("100"), BOOKINGS,
                LocalDate.of(2026, 10, 15));

        assertThat(result.function()).isEqualTo("accountBalance");
        assertThat(result.outputs()).containsExactly(
                new Figure("balance", "Current account balance", new BigDecimal("2379.45"), Unit.EUR));
        assertThat(result.keyInputs()).containsEntry("openingBalance", new BigDecimal("100.00"))
                .containsEntry("openingDate", OPENED)
                .containsEntry("bookingsSum", new BigDecimal("2279.45"))
                .containsEntry("bookingsCount", 2);
        assertThat(result.inputs()).extracting(FigureInput::value).containsExactly(new BigDecimal("100.00"),
                new BigDecimal("2279.45"), BigDecimal.valueOf(2));
        assertThat(result.inputs()).extracting(FigureInput::unit).containsExactly(Unit.EUR, Unit.EUR, Unit.COUNT);
        assertThat(result.inputs().getFirst().label()).isEqualTo("Balance when the account was opened on 1 Oct 2025");
        assertThat(result.basis()).isEqualTo("Based on your account data from 1 Oct 2025 to 15 Oct 2026.");
        assertThat(result.assumptions()).containsExactly("Includes every booking up to the end of 15 Oct 2026.");
        assertThat(result.ruleParamVersions()).isEmpty();
    }

    @Test
    void identicalInputsGiveIdenticalResults() {
        assertThat(AccountBalance.of(OPENED, BigDecimal.TEN, BOOKINGS, LocalDate.of(2026, 10, 16)))
                .isEqualTo(AccountBalance.of(OPENED, BigDecimal.TEN, BOOKINGS, LocalDate.of(2026, 10, 16)));
    }

    @Test
    void aNegativeBalanceStaysNegative() {
        CoreComputation result = AccountBalance.of(OPENED, BigDecimal.ZERO, BOOKINGS, LocalDate.of(2026, 10, 16));
        assertThat(result.outputs().getFirst().value()).isEqualByComparingTo("1280.45");
        assertThat(AccountBalance.of(OPENED, new BigDecimal("-2000"), BOOKINGS.subList(0, 1), OPENED).outputs()
                .getFirst().value()).isEqualTo(new BigDecimal("400.00"));
        assertThat(AccountBalance.of(OPENED, new BigDecimal("-90"), List.of(), OPENED).outputs().getFirst().value())
                .isEqualTo(new BigDecimal("-90.00"));
    }

    @Test
    void asOfBeforeTheOpeningDateIsRejected() {
        assertThatThrownBy(() -> AccountBalance.of(OPENED, BigDecimal.ZERO, List.of(), OPENED.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
