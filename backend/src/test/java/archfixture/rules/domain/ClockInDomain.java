package archfixture.rules.domain;

import java.time.Clock;
import java.time.Instant;

/** Deliberately broken: a domain class that reads the wall clock (AD-8). */
public class ClockInDomain {

    public Instant instant() {
        return Instant.now();
    }

    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
