package de.financetool.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import de.financetool.bank.adapter.BankJpaRepository;
import de.financetool.bank.adapter.BankRowWriter;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.personas.adapter.UserRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Keeps the committed OpenAPI snapshot, from which the Angular client is generated, equal to what the backend
 * publishes. Run with {@code -Dopenapi.update=true} to rewrite the snapshot after an intended API change.
 */
@SpringBootTest
@ActiveProfiles("webtest")
class OpenApiSnapshotTest {

    static final Path SNAPSHOT = Path.of("..", "frontend", "openapi", "openapi.json");

    @Autowired
    WebApplicationContext context;

    // No database in this test (profile "webtest"); the document does not depend on repository contents.
    @MockitoBean
    UserRepository users;

    @MockitoBean
    PersonaRepository personas;

    @MockitoBean
    PersonaStateRepository personaStates;

    @MockitoBean
    JdbcClient jdbc;

    @MockitoBean
    BankJpaRepository bankRepository;

    @MockitoBean
    BankRowWriter bankWriter;

    @Test
    void publishedDocumentMatchesCommittedSnapshot() throws Exception {
        String published = MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n") + "\n";

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, published, StandardCharsets.UTF_8);
        }

        assertThat(SNAPSHOT).as("OpenAPI snapshot %s (create it with -Dopenapi.update=true)", SNAPSHOT).exists();
        String committed = Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(published)
                .as("Backend OpenAPI differs from %s. If the change is intended, run "
                        + "'./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true', then "
                        + "'npm run api:generate' in frontend/.", SNAPSHOT)
                .isEqualTo(committed);
        assertThat(published).contains("\"Money\"", "\"ProblemCode\"", "\"ProblemDetail\"");
    }
}
