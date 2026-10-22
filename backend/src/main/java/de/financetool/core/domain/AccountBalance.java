package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Core function {@code accountBalance}: an account's balance at the end of {@code asOf}. Pure. */
public final class AccountBalance {

    public static final String FUNCTION = "accountBalance";
    public static final String OUTPUT = "balance";

    /** Customer-facing date format, e.g. "1 Oct 2025". */
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /** One booking on the account. */
    public record Booking(LocalDate date, BigDecimal amount) {

        public Booking {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(amount, "amount");
        }
    }

    private AccountBalance() {
    }

    /**
     * Opening balance plus every given booking up to and including {@code asOf}; later bookings are ignored. Bookings
     * are not checked against the opening date: {@code BankPort} and the fixture catalog never book before it.
     */
    public static CoreComputation of(LocalDate openingDate, BigDecimal openingBalance, List<Booking> bookings,
            LocalDate asOf) {
        Objects.requireNonNull(openingDate, "openingDate");
        Objects.requireNonNull(openingBalance, "openingBalance");
        Objects.requireNonNull(asOf, "asOf");
        if (asOf.isBefore(openingDate)) {
            throw new IllegalArgumentException("the account was opened after " + asOf);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (Booking booking : bookings) {
            if (!booking.date().isAfter(asOf)) {
                sum = sum.add(booking.amount());
                count++;
            }
        }
        BigDecimal opening = money(openingBalance);
        BigDecimal bookingsSum = money(sum);
        BigDecimal balance = money(opening.add(bookingsSum));

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("openingDate", openingDate);
        keyInputs.put("openingBalance", opening);
        keyInputs.put("bookingsSum", bookingsSum);
        keyInputs.put("bookingsCount", count);

        return new CoreComputation(FUNCTION, "current account balance",
                "Based on your account data from " + DATE.format(openingDate) + " to " + DATE.format(asOf) + ".",
                keyInputs,
                List.of(
                        FigureInput.number("Balance when the account was opened on " + DATE.format(openingDate),
                                opening, Unit.EUR),
                        FigureInput.number("Money in and out since then", bookingsSum, Unit.EUR),
                        FigureInput.number("Number of bookings", BigDecimal.valueOf(count), Unit.COUNT)),
                List.of(new Figure(OUTPUT, "Current account balance", balance, Unit.EUR)),
                List.of("Includes every booking up to the end of " + DATE.format(asOf) + "."),
                Map.of());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
