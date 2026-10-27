package de.financetool.research.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.domain.PersonaId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

/** Story 8.2: the researcher starts a monitoring run at the persona's simulated date. */
@ActiveProfiles("research")
class MonitoringResearchControllerTest extends PostgresIntegrationTest {

    @Autowired
    ResetPersonaUseCase resetPersona;

    /** The test database is shared by every test class: leave Lena as the fixtures define her. */
    @AfterEach
    void restoreFixtureState() {
        resetPersona.reset(new PersonaId("p-lena"));
    }

    ResultActions runMonitoring(String personaId, MockHttpSession session) throws Exception {
        return mvc.perform(post("/api/research/personas/{id}/monitoring/run", personaId).session(session)
                .with(csrf()));
    }

    @Test
    void lenasRunListsTheShortfallTransferAtL2AndARerunAddsNothing() throws Exception {
        MockHttpSession researcher = login(mvc, "researcher");
        resetPersona.reset(new PersonaId("p-lena"));

        runMonitoring("p-lena", researcher).andExpect(status().isOk())
                .andExpect(jsonPath("$.triggers[0].type").value("FORECAST_SHORTFALL"))
                .andExpect(jsonPath("$.actions", hasSize(1)))
                .andExpect(jsonPath("$.actions[0].type").value("SHORTFALL_TRANSFER"))
                .andExpect(jsonPath("$.actions[0].state").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.actions[0].level").value("L2"))
                .andExpect(jsonPath("$.actions[0].cappingCriteria[0]").value("no_covering_mandate"))
                .andExpect(jsonPath("$.notificationIds", hasSize(3)));

        runMonitoring("p-lena", researcher).andExpect(status().isOk())
                .andExpect(jsonPath("$.triggers", hasSize(0)))
                .andExpect(jsonPath("$.actions", hasSize(0)))
                .andExpect(jsonPath("$.notificationIds", hasSize(0)));
    }

    @Test
    void unknownPersonasAre404AndMalformedIds400() throws Exception {
        MockHttpSession researcher = login(mvc, "researcher");
        runMonitoring("p-nobody", researcher).andExpect(status().isNotFound());
        runMonitoring("nobody", researcher).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"p-lena", "advisor-kaya"})
    void onlyTheResearcherMayRunMonitoring(String username) throws Exception {
        runMonitoring("p-lena", login(mvc, username)).andExpect(status().isForbidden());
    }
}
