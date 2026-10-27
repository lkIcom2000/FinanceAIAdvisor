package de.financetool.actions.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The reversibility window of a transfer Fine made within a rule (FR-38): until 5 pm on the next business day (Monday
 * to Friday, no holidays) after it was made. The simulated clock knows only dates, so undo stays possible for the whole
 * deadline date. Pure.
 */
@NamedInterface("types")
public final class UndoWindow {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private UndoWindow() {
    }

    /** The last date an Action executed on {@code executedOn} can be undone. */
    public static LocalDate deadline(LocalDate executedOn) {
        Objects.requireNonNull(executedOn, "executedOn");
        LocalDate next = executedOn.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    /** Whether an Action executed on {@code executedOn} can still be undone on {@code asOf}. */
    public static boolean isOpen(LocalDate executedOn, LocalDate asOf) {
        return !asOf.isBefore(executedOn) && !asOf.isAfter(deadline(executedOn));
    }

    /** The deadline as customer text, e.g. "Thu 16 Oct, 5 pm". */
    public static String text(LocalDate deadline) {
        return DAY.format(deadline) + ", 5 pm";
    }
}
