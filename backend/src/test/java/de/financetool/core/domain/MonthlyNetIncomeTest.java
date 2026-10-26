package de.financetool.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Story 6.2: the assessment card's income pre-fill. */
class MonthlyNetIncomeTest {

    static final LocalDate AS_OF = LocalDate.of(2026, 3, 15);

    @Test
    void theMeanSalaryOfTheLastTwelveCompleteMonths() {
        List<LedgerEntry> entries = new ArrayList<>();
        // March 2025 to March 2026: thirteen salaries; March 2026 is not complete on the as-of date.
        for (int i = 0; i <= 12; i++) {
            LocalDate day = LocalDate.of(2025, 3, 28).plusMonths(i);
            entries.add(new LedgerEntry(day, new BigDecimal("3000.00"), "SALARY", "Employer", "sal", false));
        }
        entries.add(new LedgerEntry(LocalDate.of(2025, 12, 1), new BigDecimal("1200.00"), "SALARY", "Bonus", null,
                false));
        entries.add(new LedgerEntry(LocalDate.of(2025, 12, 2), new BigDecimal("500.00"), "TRANSFER", "Own", null,
                false));
        entries.add(new LedgerEntry(LocalDate.of(2025, 12, 3), new BigDecimal("-900.00"), "SALARY", "Reversal",
                null, false));

        CoreComputation c = MonthlyNetIncome.of(entries, AS_OF);

        // March 2025 to February 2026: twelve salaries and the bonus, 37,200.00 / 12; no transfer, no debit.
        assertThat(c.function()).isEqualTo(MonthlyNetIncome.FUNCTION);
        assertThat(c.outputs().getFirst().value()).isEqualByComparingTo("3100.00");
    }

    @Test
    void withoutSalaryTheIncomeIsZero() {
        CoreComputation c = MonthlyNetIncome.of(List.of(), AS_OF);
        assertThat(c.outputs().getFirst().value()).isEqualByComparingTo("0.00");
    }
}
