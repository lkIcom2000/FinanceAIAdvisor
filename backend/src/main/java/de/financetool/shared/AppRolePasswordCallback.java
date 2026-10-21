package de.financetool.shared;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * After every migrate, sets the password of the application role {@code fine_app} (AD-13) from
 * {@code SPRING_DATASOURCE_PASSWORD}, on Flyway's owner connection. The secret never appears in SQL text: PostgreSQL
 * quotes it ({@code format('%L', ?)}) from a bind parameter. An empty secret stops the migration.
 *
 * <p>Replaces the SQL callback {@code afterMigrate.sql} that V4's header comment still names (a migration's text is
 * checksummed, so the comment stays).
 */
@Component
class AppRolePasswordCallback implements Callback {

    static final String APP_ROLE = "fine_app";

    private final String password;

    AppRolePasswordCallback(@Value("${spring.datasource.password:}") String password) {
        this.password = password;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        if (password.isEmpty()) {
            throw new IllegalStateException(
                    "SPRING_DATASOURCE_PASSWORD (password of the role " + APP_ROLE + ") must be set");
        }
        Connection connection = context.getConnection();
        try (PreparedStatement quote = connection.prepareStatement(
                "SELECT format('ALTER ROLE " + APP_ROLE + " PASSWORD %L', ?::text)")) {
            quote.setString(1, password);
            try (ResultSet sql = quote.executeQuery(); Statement alter = connection.createStatement()) {
                sql.next();
                alter.execute(sql.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not set the password of the role " + APP_ROLE, e);
        }
    }

    @Override
    public String getCallbackName() {
        return "appRolePassword";
    }
}
