package de.financetool.research.application;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds changed persona fixtures once all singletons (including Flyway) are initialised and before the web server
 * starts, so no request ever sees unseeded bank state; any failure (e.g. a persona without a fixture) stops startup.
 * Switched off only in web-layer tests without a database.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.fixtures.seed-on-startup", matchIfMissing = true)
class SeedFixturesOnStartup implements SmartInitializingSingleton {

    private final SeedPersonaFixturesUseCase seed;

    SeedFixturesOnStartup(SeedPersonaFixturesUseCase seed) {
        this.seed = seed;
    }

    @Override
    public void afterSingletonsInstantiated() {
        seed.seedChanged();
    }
}
