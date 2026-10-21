package de.financetool.personas.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

/** AD-11 role separation, without the research profile (the study configuration: no researcher login). */
class RoleAccessTest extends PostgresIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/advisor/cases", "/swagger-ui/index.html", "/v3/api-docs/advisor"})
    void personaCannotReachAdvisorEndpointsOrSwagger(String path) throws Exception {
        mvc.perform(get(path).session(login(mvc, "p-lena")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void staffCannotCallPersonaEndpoints() throws Exception {
        mvc.perform(get("/api/persona").session(login(mvc, "advisor-kaya")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void advisorReachesItsSwaggerGroup() throws Exception {
        MockHttpSession advisor = login(mvc, "advisor-kaya");
        mvc.perform(get("/v3/api-docs/advisor").session(advisor)).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/customer").session(advisor)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"p-lena", "advisor-kaya"})
    void researchEndpointsDoNotExistWithoutTheResearchProfile(String username) throws Exception {
        mvc.perform(get("/api/research/anything").session(login(mvc, username)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs/research").session(login(mvc, username)))
                .andExpect(status().isForbidden());
    }

    @Test
    void researchPathIs404EvenWithoutSessionOrCsrfToken() throws Exception {
        mvc.perform(get("/api/research/anything")).andExpect(status().isNotFound());
        mvc.perform(post("/api/research/anything")).andExpect(status().isNotFound());
        mvc.perform(post("/api/research/personas/p-lena/reset")).andExpect(status().isNotFound());
        mvc.perform(post("/api/research/personas/p-lena/simulated-date")).andExpect(status().isNotFound());
        mvc.perform(get("/api/research/audit/export")).andExpect(status().isNotFound());
    }

    /** With the research profile, research paths and the research Swagger group belong to the researcher. */
    @Nested
    @ActiveProfiles("research")
    class WithResearchProfile extends PostgresIntegrationTest {

        @Test
        void onlyTheResearcherReachesResearchPaths() throws Exception {
            mvc.perform(get("/api/research/anything").session(login(mvc, "p-lena")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/research/anything").session(login(mvc, "advisor-kaya")))
                    .andExpect(status().isForbidden());
            MockHttpSession researcher = login(mvc, "researcher");
            mvc.perform(get("/v3/api-docs/research").session(researcher)).andExpect(status().isOk());
            mvc.perform(get("/swagger-ui/index.html").session(researcher)).andExpect(status().isOk());
            mvc.perform(get("/api/persona").session(researcher)).andExpect(status().isForbidden());
        }
    }
}
