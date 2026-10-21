package de.financetool.personas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends PostgresIntegrationTest {

    static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @ParameterizedTest
    @CsvSource({"p-lena, PERSONA, Lena", "p-marco, PERSONA, Marco", "advisor-kaya, ADVISOR, Kaya",
            "researcher, RESEARCHER, Researcher"})
    void validLoginReturnsRoleAndDisplayNameAndANewSession(String username, String role, String name)
            throws Exception {
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();

        MvcResult result = mvc.perform(post("/api/auth/login").session(before).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.displayName").value(name))
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(idBefore);
    }

    @ParameterizedTest
    @CsvSource({"p-lena, wrong-password", "nobody, fine-demo"})
    void wrongCredentialsAre401JsonWithoutRedirect(String username, String password) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("p-lena", DEMO_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void spaCsrfFlowWorksWithTheCookieValueAsHeader() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();

        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials("p-lena", DEMO_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login(mvc, "p-lena");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void protectedEndpointWithoutSessionIs401Json() throws Exception {
        mvc.perform(get("/api/persona"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** Logs in and returns the authenticated session. */
    public static MockHttpSession login(org.springframework.test.web.servlet.MockMvc mvc, String username)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(username, DEMO_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
