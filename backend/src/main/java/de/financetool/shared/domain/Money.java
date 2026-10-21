package de.financetool.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of money with scale 2 (HALF_UP). On the wire it is a decimal string plus currency.
 */
public record Money(BigDecimal amount, String currency) {

    public static final String EUR = "EUR";

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code: " + currency);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money eur(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    public static Money eur(String amount) {
        return eur(new BigDecimal(amount));
    }

    /** The decimal string used on the wire, e.g. {@code "1234.50"}. */
    public String amountString() {
        return amount.toPlainString();
    }
}
