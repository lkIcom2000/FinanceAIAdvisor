package de.financetool.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.PostgresIntegrationTest;
import de.financetool.shared.PersonaResettable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Story 1.7 definition of done for every later story: a table holding persona data is reset by its owner. A new
 * table with a {@code persona_id} column fails here until it is listed with its owning module, and that module
 * registers a {@link PersonaResettable}.
 */
class PersonaOwnedTablesTest extends PostgresIntegrationTest {

    /** Table → owning module (AD-12). */
    static final Map<String, String> OWNERS = Map.of(
            "users", "personas",
            "persona_state", "personas");

    /** Append-only evidence: never reset (AD-13). */
    static final Set<String> NEVER_RESET = Set.of("audit_records", "evidence_items");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    List<PersonaResettable> owners;

    @Test
    void everyPersonaOwnedTableIsResetByARegisteredOwner() {
        Set<String> tables = Set.copyOf(jdbc.queryForList("SELECT table_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND column_name = 'persona_id'", String.class));
        Set<String> registered = owners.stream().map(PersonaResettable::module).collect(Collectors.toSet());

        for (String table : tables) {
            if (NEVER_RESET.contains(table)) {
                continue;
            }
            assertThat(OWNERS).as("owner of the persona table " + table).containsKey(table);
            assertThat(registered).as("PersonaResettable of module " + OWNERS.get(table) + " (table " + table + ")")
                    .contains(OWNERS.get(table));
        }
        assertThat(tables).as("listed tables still exist").containsAll(OWNERS.keySet()).containsAll(NEVER_RESET);
    }
}
