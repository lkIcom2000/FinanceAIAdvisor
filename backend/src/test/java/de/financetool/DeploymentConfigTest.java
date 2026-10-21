package de.financetool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** AD-17: one image, one compose file; environments differ only in a fixed set of variables and the secrets. */
class DeploymentConfigTest {

    static final Path DEPLOY = Path.of("..", "deploy");

    static final Set<String> ALLOWED_APP_VARIABLES = Set.of(
            "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
            "SPRING_DATASOURCE_OWNER_USERNAME", "SPRING_DATASOURCE_OWNER_PASSWORD",
            "APP_OLLAMA_URL", "APP_AGENT_CONFIG", "SPRING_PROFILES_ACTIVE");

    /** A secret must be required from the environment: {@code ${NAME:?message}}. */
    static final Pattern REQUIRED_FROM_ENV = Pattern.compile("^\\$\\{[A-Z0-9_]+:\\?[^}]*}$");

    private final Map<String, Map<String, Object>> services = loadServices();

    @Test
    void composeHasExactlyTheThreeServicesWithPinnedImages() {
        assertThat(services).containsOnlyKeys("app", "db", "ollama");
        assertThat(services.get("db")).containsEntry("image", "pgvector/pgvector:0.8.2-pg17");
        assertThat(services.get("ollama")).containsEntry("image", "ollama/ollama:0.40.6");
        assertThat(environment("ollama")).containsEntry("OLLAMA_KEEP_ALIVE", "-1");
    }

    @Test
    void appMountsConfigDataAndModelsReadOnly() {
        assertThat(list(services.get("app"), "volumes")).containsExactlyInAnyOrder(
                "../config:/app/config:ro", "../data:/app/data:ro", "../models:/app/models:ro");
    }

    @Test
    void appEnvironmentIsLimitedToTheAllowedVariables() {
        assertThat(environment("app").keySet()).isSubsetOf(ALLOWED_APP_VARIABLES);
    }

    @Test
    void secretsComeOnlyFromTheEnvironment() {
        services.keySet().forEach(service -> environment(service).forEach((name, value) -> {
            if (name.matches(".*(PASSWORD|SECRET|TOKEN|KEY)(_.*)?$") && !name.equals("OLLAMA_KEEP_ALIVE")) {
                assertThat(value).as("%s.%s", service, name).matches(REQUIRED_FROM_ENV);
            }
        }));
    }

    @Test
    void dockerfileIsMultiStageWithPinnedBaseImagesAndAppHome() throws IOException {
        List<String> from = Files.readAllLines(DEPLOY.resolve("Dockerfile")).stream()
                .filter(line -> line.startsWith("FROM ")).toList();
        assertThat(from).hasSize(3).allSatisfy(line -> assertThat(line).doesNotContain(":latest").containsPattern(":\\d"));
        String dockerfile = Files.readString(DEPLOY.resolve("Dockerfile"));
        assertThat(dockerfile)
                .contains("ENV APP_HOME=/app")
                .contains("dist/frontend/browser/ src/main/resources/static/")
                .contains("USER fine");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> loadServices() {
        try {
            Map<String, Object> compose = new Yaml().load(Files.readString(DEPLOY.resolve("docker-compose.yml")));
            return (Map<String, Map<String, Object>>) compose.get("services");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> environment(String service) {
        Object environment = services.get(service).get("environment");
        return environment == null ? Map.of() : (Map<String, String>) environment;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> map, String key) {
        return (List<String>) map.get(key);
    }
}
