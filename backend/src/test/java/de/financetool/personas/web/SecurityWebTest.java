package de.financetool.personas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.financetool.personas.adapter.PersonaEntity;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.personas.adapter.PersonaStateEntity;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.personas.adapter.UserEntity;
import de.financetool.personas.adapter.UserRepository;
import de.financetool.shared.Role;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The real security chain and controllers with in-memory account data (no database), so login, CSRF, roles and the
 * principal-only persona scope are verified on every build. The database-backed variants are the
 * {@code PostgresIntegrationTest}s.
 */
@SpringBootTest
@ActiveProfiles("webtest")
class SecurityWebTest {

    /** BCrypt of the demo password "fine-demo", as in V2__personas.sql. */
    static final String HASH = "{bcrypt}$2a$10$ISgkb5YeLb3ZsR.SOXiHgOxgqBRQYFfUevXp9livWL56y8Lr3BBAi";

    @MockitoBean
    UserRepository users;

    @MockitoBean
    PersonaRepository personas;

    @MockitoBean
    PersonaStateRepository personaStates;

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Map<String, UserEntity> accounts = Map.of(
                "p-lena", user("p-lena", Role.PERSONA, "p-lena", null),
                "p-marco", user("p-marco", Role.PERSONA, "p-marco", null),
                "advisor-kaya", user("advisor-kaya", Role.ADVISOR, null, "Kaya"));
        Map<String, PersonaEntity> people = Map.of(
                "p-lena", persona("p-lena", "Lena"), "p-marco", persona("p-marco", "Marco"));
        when(users.findByUsername(anyString())).thenAnswer(i -> Optional.ofNullable(accounts.get(i.<String>getArgument(0))));
        when(personas.findById(anyString())).thenAnswer(i -> Optional.ofNullable(people.get(i.<String>getArgument(0))));
        when(personas.find(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(personaStates.findByPersonaId(anyString())).thenAnswer(i -> Optional.of(state(i.getArgument(0))));
        when(personaStates.find(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
    }

    @Test
    void loginReturnsRoleAndNameAsJson() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(AuthIntegrationTest.credentials("p-lena", "fine-demo")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.role").value("PERSONA"))
                .andExpect(jsonPath("$.displayName").value("Lena"));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSame401() throws Exception {
        for (String body : new String[] {AuthIntegrationTest.credentials("p-lena", "nope"),
                AuthIntegrationTest.credentials("nobody", "fine-demo")}) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
    }

    @Test
    void missingCsrfIs403Json() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(AuthIntegrationTest.credentials("p-lena", "fine-demo")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void logoutWithoutCsrfIs403Json() throws Exception {
        mvc.perform(post("/api/auth/logout").session(login("p-lena")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void staffReachSwaggerUi() throws Exception {
        MockHttpSession advisor = login("advisor-kaya");
        mvc.perform(get("/swagger-ui/index.html").session(advisor)).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config").session(advisor)).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs.yaml").session(advisor)).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPathIsServedByTheSpa() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void personaComesOnlyFromTheSession() throws Exception {
        MockHttpSession lena = login("p-lena");
        String body = mvc.perform(get("/api/persona").session(lena).param("personaId", "p-marco"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("p-lena").doesNotContain("p-marco").doesNotContain("Marco")
                .as("no simulated date without the research profile").doesNotContain("today");
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/persona").session(login("advisor-kaya"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/advisor/cases").session(login("p-lena"))).andExpect(status().isForbidden());
        mvc.perform(get("/swagger-ui/index.html").session(login("p-lena"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/persona")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/research/anything").session(login("p-lena"))).andExpect(status().isNotFound());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession lena = login("p-lena");
        mvc.perform(post("/api/auth/logout").session(lena).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
        mvc.perform(get("/api/auth/me").session(lena)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AuthIntegrationTest.credentials(username, "fine-demo")))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private static UserEntity user(String username, Role role, String personaId, String displayName) {
        UserEntity user = mock(UserEntity.class);
        when(user.getUsername()).thenReturn(username);
        when(user.getPasswordHash()).thenReturn(HASH);
        when(user.getRole()).thenReturn(role);
        when(user.getPersonaId()).thenReturn(personaId);
        when(user.getDisplayName()).thenReturn(displayName);
        return user;
    }

    static final LocalDate SIMULATED_DATE = LocalDate.of(2026, 10, 20);

    private static PersonaStateEntity state(String personaId) {
        PersonaStateEntity state = mock(PersonaStateEntity.class);
        when(state.getPersonaId()).thenReturn(personaId);
        when(state.getSimulatedDate()).thenReturn(SIMULATED_DATE);
        when(state.getFixtureVersion()).thenReturn("pre-fixtures");
        return state;
    }

    private static PersonaEntity persona(String id, String name) {
        PersonaEntity persona = mock(PersonaEntity.class);
        when(persona.getId()).thenReturn(id);
        when(persona.getDisplayName()).thenReturn(name);
        return persona;
    }
}
