package de.financetool.research.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import de.financetool.advisory.adapter.NluCacheRepository;
import de.financetool.personas.application.ShowPersonaUseCase;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

/** Persona reset and simulated date (Story 1.7), research profile. */
@ActiveProfiles("research")
@ExtendWith(OutputCaptureExtension.class)
class PersonaResetIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ShowPersonaUseCase showPersona;

    @Autowired
    ResetPersonaUseCase resetPersona;

    @Autowired
    PersonaFixtureCatalog fixtures;

    @Autowired
    NluCacheRepository nluCache;

    /** The test database is shared by every test class: leave the personas as the fixtures define them. */
    @AfterEach
    void restoreFixtureState() {
        resetPersona.reset(new PersonaId("p-jonas"));
        resetPersona.reset(new PersonaId("p-petra"));
    }

    ResultActions reset(String personaId, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/research/personas/{id}/reset", personaId).session(session).with(csrf()));
    }

    ResultActions setDate(String personaId, String body, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/research/personas/{id}/simulated-date", personaId).session(session)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    Map<String, Object> state(String personaId) {
        return jdbc.queryForMap("SELECT * FROM persona_state WHERE persona_id = ?", personaId);
    }

    long auditRows() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM audit_records) + (SELECT count(*) FROM evidence_items)",
                Long.class);
    }

    @Test
    void resetDeletesOnlyThisPersonasNluCacheRowsAndTheCacheNeverAnswersAcrossPersonas() {
        PersonaScope jonas = new PersonaScope(new PersonaId("p-jonas"));
        PersonaScope petra = new PersonaScope(new PersonaId("p-petra"));
        NluCacheRepository.Entry entry = new NluCacheRepository.Entry("{}", true);
        nluCache.put(jonas, "reset-test-jonas", entry);
        nluCache.put(petra, "reset-test-petra", entry);
        assertThat(nluCache.find(jonas, "reset-test-petra")).as("another persona's row").isEmpty();

        resetPersona.reset(jonas.personaId());

        assertThat(nluCache.find(jonas, "reset-test-jonas")).isEmpty();
        assertThat(nluCache.find(petra, "reset-test-petra")).contains(entry);
    }

    @Test
    void resetRestoresTheFixtureStartAndIsReproducible() throws Exception {
        MockHttpSession researcher = login(mvc, "researcher");
        long audit = auditRows();

        jdbc.update("UPDATE persona_state SET simulated_date = DATE '2027-01-15', fixture_version = 'changed' "
                + "WHERE persona_id = 'p-jonas'");
        reset("p-jonas", researcher).andExpect(status().isNoContent());
        Map<String, Object> first = state("p-jonas");

        setDate("p-jonas", "{\"date\":\"2026-12-24\"}", researcher).andExpect(status().isNoContent());
        jdbc.update("UPDATE persona_state SET fixture_version = 'changed again' WHERE persona_id = 'p-jonas'");
        reset("p-jonas", researcher).andExpect(status().isNoContent());

        PersonaId jonas = new PersonaId("p-jonas");
        assertThat(state("p-jonas")).isEqualTo(first)
                .containsEntry("simulated_date", java.sql.Date.valueOf(fixtures.fixture(jonas).simulatedDate()))
                .containsEntry("fixture_version", fixtures.version(jonas));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persona_state WHERE fixture_version = 'pre-fixtures'",
                Long.class)).as("the V3 placeholder is gone").isZero();
        assertThat(auditRows()).as("audit is never reset").isEqualTo(audit);
    }

    @Test
    void theNextRequestContextRunsOnTheNewSimulatedDate(CapturedOutput output) throws Exception {
        setDate("p-petra", "{\"date\":\"2026-11-06\"}", login(mvc, "researcher")).andExpect(status().isNoContent());

        assertThat(showPersona.show(new PersonaScope(new PersonaId("p-petra"))).asOf())
                .isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(output).as("researcher actions are logged").contains("Simulated date set")
                .contains("\"personaId\":\"p-petra\"").contains("\"researcher\":\"researcher\"");
    }

    @Test
    void unknownPersonasAre404AndMalformedIds400() throws Exception {
        MockHttpSession researcher = login(mvc, "researcher");
        reset("p-nobody", researcher).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        setDate("p-nobody", "{\"date\":\"2026-11-06\"}", researcher).andExpect(status().isNotFound());
        reset("nobody", researcher).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"date\":\"06.11.2026\"}", "not json"})
    void aMissingOrInvalidDateIs400(String body) throws Exception {
        setDate("p-petra", body, login(mvc, "researcher")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"p-lena", "advisor-kaya"})
    void onlyTheResearcherMayResetOrSetTheDate(String username) throws Exception {
        MockHttpSession session = login(mvc, username);
        reset("p-lena", session).andExpect(status().isForbidden());
        setDate("p-lena", "{\"date\":\"2026-11-06\"}", session).andExpect(status().isForbidden());
    }
}
