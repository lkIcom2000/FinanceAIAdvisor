package de.financetool.research.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds changed persona fixtures before the app serves requests; any failure (e.g. a persona without a fixture) stops
 * startup. Switched off only in web-layer tests without a database.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.fixtures.seed-on-startup", matchIfMissing = true)
class SeedFixturesOnStartup implements ApplicationRunner {

    private final SeedPersonaFixturesUseCase seed;

    SeedFixturesOnStartup(SeedPersonaFixturesUseCase seed) {
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed.seedChanged();
    }
}
