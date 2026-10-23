package de.financetool.advisory.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.advisory.domain.MessageClass;
import de.financetool.shared.AppHome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Story 3.3 review: the prompt files a turn needs are checked at startup (each failure names the file), and the
 * entity registry loader skips empty items and rejects malformed files.
 */
class PromptAndRegistryFilesTest {

    static final Path REPO = Path.of("..");

    @TempDir
    Path home;

    /** An APP_HOME with a copy of the repository's prompt files. */
    Path appHome() throws IOException {
        for (String dir : List.of("config/prompts", "data", "models")) {
            Files.createDirectories(home.resolve(dir));
        }
        try (var files = Files.list(REPO.resolve("config/prompts"))) {
            for (Path file : files.toList()) {
                Files.copy(file, home.resolve("config/prompts").resolve(file.getFileName()));
            }
        }
        return home;
    }

    PromptFiles promptFiles() {
        return new PromptFiles(new AppHome(home.toString()));
    }

    @Test
    void theRepositorysPromptFilesPassAndTheFewShotFileIsParsedOnce() throws IOException {
        appHome();
        PromptFiles prompts = promptFiles();
        assertThat(prompts.nluFewShots()).hasSize(5).allSatisfy(example -> {
            assertThat(example.message()).isNotBlank();
            assertThat(example.extraction()).isInstanceOf(Map.class);
        });
        assertThat(prompts.nluFewShots()).isSameAs(prompts.nluFewShots());
    }

    @Test
    void everyRequiredPromptFileIsCheckedAtStartup() throws IOException {
        List<String> required = new java.util.ArrayList<>(List.of("nlu-system.txt", "nlu-few-shot.json"));
        for (MessageClass c : MessageClass.values()) {
            required.add("class-" + c + ".txt");
        }
        appHome();
        for (String file : required) {
            Path path = home.resolve("config/prompts").resolve(file);
            byte[] content = Files.readAllBytes(path);
            Files.delete(path);
            assertThatThrownBy(this::promptFiles).as(file).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(file);
            Files.write(path, content);
        }
    }

    @Test
    void aFewShotFileThatIsNoListOfPairsFailsNamingTheFile() throws IOException {
        appHome();
        Path fewShot = home.resolve("config/prompts/nlu-few-shot.json");
        for (String bad : List.of("null", "{}", "[[\"only a message\"]]", "[[1, {}]]", "[[\"m\", null]]", "not json")) {
            Files.writeString(fewShot, bad, StandardCharsets.UTF_8);
            assertThatThrownBy(this::promptFiles).as(bad).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nlu-few-shot.json");
        }
    }

    @Test
    void theRegistryLoaderReadsTheRepositoryFile() {
        Map<String, List<String>> lists = ClassificationConfiguration.load(REPO.resolve("data/rules/known_entities.yaml"));
        assertThat(lists).containsKeys("laws_and_regulations", "institutions", "products_and_isins",
                "known_guarantees", "suspicious_claim_patterns");
        assertThat(lists.get("laws_and_regulations")).contains("MiFID II", "Rürup");
        assertThat(lists.get("products_and_isins")).isEmpty();
    }

    @Test
    void theRegistryLoaderSkipsNullAndBlankItems() throws IOException {
        Path file = home.resolve("known.yaml");
        Files.writeString(file, "laws_and_regulations:\n  - MiFID II\n  -\n  - \"  \"\n  - 2026\ninstitutions:\n",
                StandardCharsets.UTF_8);
        assertThat(ClassificationConfiguration.load(file)).containsExactly(
                Map.entry("laws_and_regulations", List.of("MiFID II", "2026")), Map.entry("institutions", List.of()));
    }

    @Test
    void aScalarValueOrANonMapRootFails() throws IOException {
        Path scalar = home.resolve("scalar.yaml");
        Files.writeString(scalar, "laws_and_regulations: MiFID II\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ClassificationConfiguration.load(scalar)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("laws_and_regulations");
        Path list = home.resolve("list.yaml");
        Files.writeString(list, "- MiFID II\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ClassificationConfiguration.load(list)).isInstanceOf(IllegalStateException.class);
    }
}
