package de.financetool.personas;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.PostgresIntegrationTest;
import de.financetool.personas.application.ShowPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** V3: every persona has a simulated date and a fixture version, and use cases run on that date. */
class PersonaStateMigrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ShowPersonaUseCase showPersona;

    @Test
    void everyPersonaHasAClockAndAFixtureVersion() {
        List<String> missing = jdbc.queryForList(
                "SELECT p.id FROM personas p LEFT JOIN persona_state s ON s.persona_id = p.id "
                        + "WHERE s.simulated_date IS NULL OR s.fixture_version IS NULL", String.class);
        assertThat(missing).isEmpty();
    }

    @Test
    void theUseCaseRunsOnThePersonasSimulatedDate() {
        jdbc.update("UPDATE persona_state SET simulated_date = DATE '2026-11-06' WHERE persona_id = 'p-marco'");

        assertThat(showPersona.show(new PersonaScope(new PersonaId("p-marco"))).asOf())
                .isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(showPersona.show(new PersonaScope(new PersonaId("p-lena"))).asOf())
                .isEqualTo(LocalDate.of(2026, 10, 20));
    }
}
