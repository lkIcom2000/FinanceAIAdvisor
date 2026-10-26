package de.financetool.profile.adapter;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The tables {@code suitability_profiles} and {@code needs_profiles}, one row per field. Every read and write takes a
 * {@link PersonaScope} (AD-11); only persona reset deletes by persona id.
 */
@Repository
public class ProfileRepository {

    public enum Table {
        SUITABILITY("suitability_profiles"), NEEDS("needs_profiles");

        final String name;

        Table(String name) {
            this.name = name;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(PersonaScope scope, Table table, String field, Object value, Instant confirmedAt) {
        jdbc.sql("INSERT INTO " + table.name + " (persona_id, field, value, confirmed_at) VALUES (?, ?, ?::jsonb, ?) "
                + "ON CONFLICT (persona_id, field) DO UPDATE SET value = EXCLUDED.value, "
                + "confirmed_at = EXCLUDED.confirmed_at")
                .params(scope.id(), field, JSON.writeValueAsString(value), Timestamp.from(confirmedAt))
                .update();
    }

    /** The persona's fields of {@code table}, by field name. */
    public Map<String, Object> fields(PersonaScope scope, Table table) {
        Map<String, Object> out = new LinkedHashMap<>();
        jdbc.sql("SELECT field, value FROM " + table.name + " WHERE persona_id = ? ORDER BY field")
                .param(scope.id())
                .query((rs, row) -> Map.entry(rs.getString("field"), JSON.readValue(rs.getString("value"), Object.class)))
                .list()
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** Persona reset: deletes the persona's rows in both tables; returns the deleted rows. */
    public int deletePersonaRows(PersonaId personaId) {
        int deleted = 0;
        for (Table table : Table.values()) {
            deleted += jdbc.sql("DELETE FROM " + table.name + " WHERE persona_id = ?").param(personaId.value())
                    .update();
        }
        return deleted;
    }
}
