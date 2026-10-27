package de.financetool.actions.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Story 9.3: the undo deadline is the next business day after the transfer, 5 pm. */
class UndoWindowTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2025-10-15, 2025-10-16, 'Thu 16 Oct, 5 pm'",  // Wednesday
            "2025-10-17, 2025-10-20, 'Mon 20 Oct, 5 pm'",  // Friday
            "2025-10-18, 2025-10-20, 'Mon 20 Oct, 5 pm'",  // Saturday
            "2025-10-19, 2025-10-20, 'Mon 20 Oct, 5 pm'",  // Sunday
    })
    void theDeadlineIsTheNextBusinessDay(LocalDate executedOn, LocalDate deadline, String text) {
        assertThat(UndoWindow.deadline(executedOn)).isEqualTo(deadline);
        assertThat(UndoWindow.text(deadline)).isEqualTo(text);
    }

    @ParameterizedTest(name = "{1} -> {2}")
    @CsvSource({
            "2025-10-17, 2025-10-16, false",  // before the transfer
            "2025-10-17, 2025-10-17, true",
            "2025-10-17, 2025-10-20, true",   // Monday after a Friday transfer
            "2025-10-17, 2025-10-21, false",
    })
    void theWindowIsOpenFromTheTransferToTheDeadlineDate(LocalDate executedOn, LocalDate asOf, boolean open) {
        assertThat(UndoWindow.isOpen(executedOn, asOf)).isEqualTo(open);
    }
}
