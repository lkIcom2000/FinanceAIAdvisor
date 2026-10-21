package de.financetool.personas.web;

import static de.financetool.personas.web.AuthIntegrationTest.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import de.financetool.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * FR-1 / AD-11: logged in as {@code p-lena}, no {@code /api/**} endpoint returns {@code p-marco} data, whatever
 * persona id the request carries. Sweeps every registered mapping, so later endpoints are covered automatically.
 */
class PersonaIsolationTest extends PostgresIntegrationTest {

    static final Set<String> SESSION_ENDPOINTS = Set.of("/api/auth/login", "/api/auth/logout", "/api/auth/csrf");
    static final String OTHER = "p-marco";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void lenaNeverSeesMarcosData() throws Exception {
        MockHttpSession lena = login(mvc, "p-lena");
        List<String> checked = new ArrayList<>();

        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/") || SESSION_ENDPOINTS.contains(pattern)) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", OTHER);
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    MockHttpServletResponse response = mvc.perform(
                                    request(HttpMethod.valueOf(method.name()), path)
                                            .session(lena).with(csrf())
                                            .param("personaId", OTHER)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"personaId\":\"" + OTHER + "\"}"))
                            .andReturn().getResponse();
                    String body = response.getContentAsString(StandardCharsets.UTF_8);
                    assertThat(response.getStatus()).as("%s %s must not fail on the server", method, path)
                            .isLessThan(500);
                    assertThat(body).as("%s %s", method, path).doesNotContain(OTHER).doesNotContain("Marco");
                    checked.add(method + " " + path);
                    if (path.equals("/api/persona") && method == RequestMethod.GET) {
                        assertThat(body).as("the scope was applied: Lena's own data").contains("p-lena");
                    }
                }
            }
        }

        assertThat(checked).as("swept endpoints").contains("GET /api/persona");
    }
}
