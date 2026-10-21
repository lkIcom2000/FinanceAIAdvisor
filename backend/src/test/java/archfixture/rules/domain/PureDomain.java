package archfixture.rules.domain;

import java.time.LocalDate;

/** A conforming domain class: plain Java, time passed in as an argument. */
public record PureDomain(LocalDate asOf) {

    public LocalDate nextDay() {
        return asOf.plusDays(1);
    }
}
