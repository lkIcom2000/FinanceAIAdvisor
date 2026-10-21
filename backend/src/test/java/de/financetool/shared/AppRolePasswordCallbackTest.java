package de.financetool.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;

/** The app role's password is set after every migrate and must not be empty (AD-13). */
class AppRolePasswordCallbackTest {

    @Test
    void runsAfterEveryMigrateOnly() {
        AppRolePasswordCallback callback = new AppRolePasswordCallback("secret");
        assertThat(callback.supports(Event.AFTER_MIGRATE, null)).isTrue();
        assertThat(callback.supports(Event.BEFORE_MIGRATE, null)).isFalse();
    }

    @Test
    void anEmptyPasswordStopsTheMigration() {
        assertThatThrownBy(() -> new AppRolePasswordCallback("").handle(Event.AFTER_MIGRATE, mock(Context.class)))
                .hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
    }
}
