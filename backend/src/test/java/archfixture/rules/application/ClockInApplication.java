package archfixture.rules.application;

import java.time.LocalDate;

/** Deliberately broken: an application class that reads the wall clock (AD-8). */
public class ClockInApplication {

    public LocalDate today() {
        return LocalDate.now();
    }
}
