package de.financetool.audit.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.financetool.PostgresIntegrationTest;
import de.financetool.personas.PersonaClock;
import de.financetool.shared.domain.PersonaId;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-8 end to end (Epic 1 retro A7, adapted): an Overview figure is stored as evidence by the first production
 * {@code EvidenceStore} caller, and its provenance is served only to its own persona.
 */
class ProvenanceEndToEndTest extends PostgresIntegrationTest {

    static final PersonaId LENA = new PersonaId("p-lena");
    static final LocalDate START = LocalDate.of(2026, 10, 15);

    @Autowired
    PersonaClock clock;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void lenaAtTheFixtureStart() {
        simulatedDate(START);
    }

    void simulatedDate(LocalDate date) {
        transaction.executeWithoutResult(tx -> clock.setSimulatedDate(LENA, date));
    }

    String homeFigure(MockHttpSession session) throws Exception {
        return mvc.perform(get("/api/overview/home").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void theHomeFigureIsTraceableByItsOwnPersonaOnly() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String home = homeFigure(lena);
        String provId = JsonPath.read(home, "$.currentAccount.provId");
        assertThat(provId).matches("[0-9a-f]{16}");
        assertThat((String) JsonPath.read(home, "$.currentAccount.value")).isEqualTo("1974.00");
        assertThat((String) JsonPath.read(home, "$.currentAccount.unit")).isEqualTo("EUR");

        // Same call again: same key, still one evidence row.
        assertThat((String) JsonPath.read(homeFigure(lena), "$.currentAccount.provId")).isEqualTo(provId);
        assertThat(jdbc.queryForList("SELECT origin FROM evidence_items WHERE prov_id = ? AND persona_id = ?",
                String.class, provId, "p-lena")).containsExactly("overview");

        mvc.perform(get("/api/provenance/{id}", provId).session(lena))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provId").value(provId))
                .andExpect(jsonPath("$.value").value("1974.00"))
                .andExpect(jsonPath("$.unit").value("EUR"))
                .andExpect(jsonPath("$.label").value("Current account balance"))
                .andExpect(jsonPath("$.asOf").value("2026-10-15"))
                .andExpect(jsonPath("$.source.kind").value("CORE_FUNCTION"))
                .andExpect(jsonPath("$.source.description").value(org.hamcrest.Matchers.startsWith(
                        "Calculated by Fine's calculation engine (current account balance), not by the language "
                                + "model. Based on your account data from ")))
                .andExpect(jsonPath("$.inputs.length()").value(3))
                .andExpect(jsonPath("$.assumptions[0]").value("Includes every booking up to the end of 15 Oct 2026."))
                .andExpect(jsonPath("$.checks").isEmpty())
                .andExpect(jsonPath("$.function").doesNotExist());

        MockHttpSession marco = login(mvc, "p-marco");
        String foreign = mvc.perform(get("/api/provenance/{id}", provId).session(marco))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknown = mvc.perform(get("/api/provenance/{id}", "0000000000000000").session(marco))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(foreign).isEqualTo(unknown).doesNotContain(provId);
    }

    @Test
    void aChangedSimulatedDateGivesAnotherKeyAndValue() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        String before = homeFigure(lena);
        simulatedDate(START.minusDays(14));
        String after = homeFigure(lena);

        assertThat((String) JsonPath.read(after, "$.currentAccount.provId"))
                .isNotEqualTo(JsonPath.read(before, "$.currentAccount.provId"));
        assertThat((String) JsonPath.read(after, "$.currentAccount.value"))
                .isNotEqualTo(JsonPath.read(before, "$.currentAccount.value"));
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        for (String id : new String[] {"xyz", "0123456789ABCDEF", "0123456789abcdef0"}) {
            mvc.perform(get("/api/provenance/{id}", id).session(lena))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void withoutAPersonaSessionTheEndpointsAreClosed() throws Exception {
        for (String path : new String[] {"/api/provenance/0123456789abcdef", "/api/overview/home"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(get(path).session(login(mvc, "advisor-kaya"))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }
}
