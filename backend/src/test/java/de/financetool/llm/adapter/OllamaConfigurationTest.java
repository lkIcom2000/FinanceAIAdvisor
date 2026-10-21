package de.financetool.llm.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.shared.AppHome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.Yaml;

/** AD-18: the startup model checks are on unless a test switches them off. */
class OllamaConfigurationTest {

    final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(OllamaConfiguration.class)
            .withBean(AppHome.class, () -> new AppHome(".."))
            .withBean(de.financetool.shared.config.AgentConfiguration.class,
                    () -> OllamaLlmAdapterTest.agent("phrase-a"))
            .withPropertyValues("app.ollama.url=http://ollama:11434");

    @Test
    void theStartupCheckIsRegisteredByDefault() {
        context.run(ctx -> assertThat(ctx).hasSingleBean(ApplicationRunner.class));
        context.withPropertyValues("app.llm.verify-on-startup=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ApplicationRunner.class));
        context.withPropertyValues("app.llm.verify-on-startup=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ApplicationRunner.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theShippedConfigurationKeepsTheCheckOn() throws Exception {
        Map<String, Object> yaml = new Yaml().load(Files.readString(Path.of("src/main/resources/application.yaml")));
        Map<String, Object> llm = (Map<String, Object>) ((Map<String, Object>) yaml.get("app")).get("llm");
        assertThat(llm.get("verify-on-startup")).isEqualTo(true);
    }
}
