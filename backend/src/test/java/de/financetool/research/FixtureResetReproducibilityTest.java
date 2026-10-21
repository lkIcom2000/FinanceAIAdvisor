package de.financetool.research;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.PostgresIntegrationTest;
import de.financetool.bank.AccountType;
import de.financetool.bank.BankPort;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.research.application.SeedPersonaFixturesUseCase;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Retro item A3: a fresh install, one reset and two resets leave the same rows in the five bank tables and
 * {@code persona_state}; startup seeding re-seeds only personas whose fixture changed.
 */
@ActiveProfiles("research")
@ExtendWith(OutputCaptureExtension.class)
class FixtureResetReproducibilityTest extends PostgresIntegrationTest {

    static final PersonaId JONAS = new PersonaId("p-jonas");
    static final List<String> BANK_TABLES = List.of("accounts", "contracts", "transactions", "holdings",
            "savings_plans");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ResetPersonaUseCase resetPersona;

    @Autowired
    SeedPersonaFixturesUseCase seed;

    @Autowired
    PersonaFixtureCatalog fixtures;

    @Autowired
    BankPort bankPort;

    @Autowired
    BankReadPort bank;

    @Autowired
    TransactionTemplate tx;

    @AfterEach
    void restore() {
        resetPersona.reset(JONAS);
    }

    Map<String, List<Map<String, Object>>> dump(PersonaId personaId) {
        Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();
        for (String table : BANK_TABLES) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE persona_id = ? ORDER BY id",
                    personaId.value()));
        }
        rows.put("persona_state", jdbc.queryForList("SELECT * FROM persona_state WHERE persona_id = ?",
                personaId.value()));
        return rows;
    }

    /** As after V3 on a fresh database: placeholder clock, no bank rows. */
    void wipeToFreshInstall(PersonaId personaId) {
        tx.executeWithoutResult(status -> {
            for (String table : List.of("transactions", "savings_plans", "holdings", "contracts", "accounts")) {
                jdbc.update("DELETE FROM " + table + " WHERE persona_id = ?", personaId.value());
            }
            jdbc.update("UPDATE persona_state SET simulated_date = DATE '2026-10-20', fixture_version = 'pre-fixtures'"
                    + " WHERE persona_id = ?", personaId.value());
        });
    }

    void changeEverything(PersonaId personaId) {
        BankSnapshot snapshot = bank.snapshot(personaId, LocalDate.of(2026, 10, 15));
        tx.executeWithoutResult(status -> bankPort.transfer(personaId, account(snapshot, AccountType.OVERNIGHT),
                account(snapshot, AccountType.CURRENT), new BigDecimal("75.00"), LocalDate.of(2026, 10, 15),
                "Changed during the session"));
        jdbc.update("UPDATE persona_state SET simulated_date = DATE '2026-12-24' WHERE persona_id = ?",
                personaId.value());
    }

    @Test
    void freshInstallResetOnceAndResetTwiceGiveTheSameRows() {
        wipeToFreshInstall(JONAS);
        assertThat(seed.seedChanged()).containsExactly(JONAS);
        Map<String, List<Map<String, Object>>> fresh = dump(JONAS);

        PersonaFixture fixture = fixtures.fixture(JONAS);
        assertThat(fresh.get("accounts")).hasSize(fixture.accounts().size());
        assertThat(fresh.get("contracts")).hasSize(fixture.contracts().size());
        assertThat(fresh.get("transactions")).hasSize(fixture.transactions().size());
        assertThat(fresh.get("holdings")).hasSize(fixture.holdings().size()).isNotEmpty();
        assertThat(fresh.get("savings_plans")).hasSize(fixture.savingsPlans().size()).isNotEmpty();
        assertThat(fresh.get("persona_state")).singleElement().satisfies(state -> assertThat(state)
                .containsEntry("simulated_date", java.sql.Date.valueOf(fixture.simulatedDate()))
                .containsEntry("fixture_version", fixtures.version(JONAS)));

        changeEverything(JONAS);
        assertThat(dump(JONAS)).isNotEqualTo(fresh);
        resetPersona.reset(JONAS);
        assertThat(dump(JONAS)).as("reset once").isEqualTo(fresh);

        changeEverything(JONAS);
        resetPersona.reset(JONAS);
        resetPersona.reset(JONAS);
        assertThat(dump(JONAS)).as("reset twice").isEqualTo(fresh);
    }

    @Test
    void anUnchangedRestartSeedsNothingSoSessionChangesSurvive() {
        changeEverything(JONAS);
        Map<String, List<Map<String, Object>>> changed = dump(JONAS);

        assertThat(seed.seedChanged()).isEmpty();

        assertThat(dump(JONAS)).isEqualTo(changed);
    }

    @Test
    void aChangedFixtureIsReseededAndLogged(CapturedOutput output) {
        resetPersona.reset(JONAS);
        Map<String, List<Map<String, Object>>> fixtureState = dump(JONAS);
        changeEverything(JONAS);
        jdbc.update("UPDATE persona_state SET fixture_version = 'outdated' WHERE persona_id = ?", JONAS.value());

        assertThat(seed.seedChanged()).containsExactly(JONAS);

        assertThat(dump(JONAS)).isEqualTo(fixtureState);
        assertThat(output).contains("Persona fixture seeded").contains("\"personaId\":\"p-jonas\"")
                .contains("\"oldVersion\":\"outdated\"").contains("\"newVersion\":\"" + fixtures.version(JONAS) + "\"");
    }

    private static java.util.UUID account(BankSnapshot snapshot, AccountType type) {
        return snapshot.accounts().stream().filter(a -> a.type() == type).findFirst().orElseThrow().id();
    }
}
