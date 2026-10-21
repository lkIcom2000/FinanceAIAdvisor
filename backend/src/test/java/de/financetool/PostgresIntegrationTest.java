package de.financetool;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for tests against a real PostgreSQL with pgvector, migrated by Flyway. Skipped when Docker is unavailable
 * (Story 1.3 decision); such a run does not count as verified.
 *
 * <p>One container for the whole test run (singleton pattern, removed by Ryuk at JVM exit). A per-class
 * {@code @Container} would be stopped after the first class while Spring's cached context still points at it.
 *
 * <p>{@code -Dfine.it.required=true} turns the skip into a failure, for review and release runs.
 *
 * <p>As in production, Flyway migrates as the owner (the container user) and the application connects as the
 * non-owner role {@code fine_app} (AD-13).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    public static final String DEMO_PASSWORD = "fine-demo";

    public static final String APP_DB_PASSWORD = "fine-app's test";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        } else if (Boolean.getBoolean("fine.it.required")) {
            throw new IllegalStateException("fine.it.required is set but Docker is not available");
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "fine_app");
        registry.add("spring.datasource.password", () -> APP_DB_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        // No Ollama in tests; the adapter is tested against a mock server.
        registry.add("app.llm.verify-on-startup", () -> "false");
    }

    @Autowired
    WebApplicationContext context;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
}
