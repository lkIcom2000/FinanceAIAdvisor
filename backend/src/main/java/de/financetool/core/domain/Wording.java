package de.financetool.core.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Customer-facing wording of numbers and dates in Core texts (real minus sign, "1,240.00 EUR", "1 Nov 2026"). */
final class Wording {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    static final char MINUS = '\u2212';

    private Wording() {
    }

    static String date(LocalDate date) {
        return DATE.format(date);
    }

    static String month(YearMonth month) {
        return MONTH.format(month);
    }

    /** "1,240.00 EUR", "−90.00 EUR". */
    static String eur(BigDecimal amount) {
        return signed(amount, false) + " EUR";
    }

    /** "+2,400.00 EUR", "−850.00 EUR". */
    static String signedEur(BigDecimal amount) {
        return signed(amount, true) + " EUR";
    }

    /** A fraction as a percentage without trailing zeros: 0.15 is "15 %", 0.955 is "95.5 %". */
    static String percent(BigDecimal fraction) {
        return fraction.movePointRight(2).stripTrailingZeros().toPlainString() + " %";
    }

    private static String signed(BigDecimal amount, boolean plus) {
        BigDecimal cents = amount.setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        String digits = format.format(cents.abs());
        if (cents.signum() < 0) {
            return MINUS + digits;
        }
        return plus && cents.signum() > 0 ? "+" + digits : digits;
    }
}
