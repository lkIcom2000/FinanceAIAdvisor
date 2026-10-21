package de.financetool.llm.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * AD-18: every model an Agent Configuration binds is listed in {@code models/tags.txt} and pinned in
 * {@code models/lock.json}, so a configuration edit cannot break startup with a green build.
 */
class ModelLockConsistencyTest {

    static final Path HOME = Path.of("..");

    @Test
    @SuppressWarnings("unchecked")
    void everyBoundModelIsListedAndLocked() throws IOException {
        List<String> listed = Files.readAllLines(HOME.resolve("models/tags.txt")).stream()
                .map(line -> line.replaceAll("#.*", "").strip()).filter(line -> !line.isEmpty()).toList();
        ModelLock lock = ModelLock.read(HOME.resolve("models")).orElseThrow();

        List<Path> configs;
        try (Stream<Path> files = Files.list(HOME.resolve("config/agent"))) {
            configs = files.filter(p -> p.toString().endsWith(".yaml")).toList();
        }
        assertThat(configs).isNotEmpty();
        for (Path config : configs) {
            Map<String, Object> agent = (Map<String, Object>) ((Map<String, Object>) new Yaml()
                    .load(Files.readString(config))).get("agent");
            Map<String, String> llm = (Map<String, String>) agent.get("llm");
            for (String role : List.of("phrasing", "nlu", "judge", "embedding")) {
                String tag = llm.get(role);
                assertThat(listed).as(config.getFileName() + " " + role + " in models/tags.txt").contains(tag);
                assertThat(lock.find(tag)).as(config.getFileName() + " " + role + " in models/lock.json").isPresent();
            }
        }
        assertThat(lock.models()).allSatisfy(entry -> {
            assertThat(entry.digest()).matches("[0-9a-f]{64}");
            assertThat(entry.ggufSha256()).matches("[0-9a-f]{64}");
        });
    }
}
