package de.financetool.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The committed fixtures are exactly what the seeded generator produces. After an intended change to
 * {@link PersonaFixtureGenerator}, run {@code ./mvnw test -Dtest=PersonaFixturesGeneratedTest
 * -Dfine.fixtures.write=true} to rewrite {@code data/personas/p-*.json}.
 */
class PersonaFixturesGeneratedTest {

    @Test
    void committedFixturesEqualTheGeneratorOutput() throws IOException {
        Map<String, String> generated = PersonaFixtureGenerator.generateAll();
        assertThat(PersonaFixtureGenerator.generateAll()).as("generation is deterministic").isEqualTo(generated);

        if (Boolean.getBoolean("fine.fixtures.write")) {
            for (Map.Entry<String, String> entry : generated.entrySet()) {
                Files.writeString(file(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
            }
        }

        try (Stream<Path> files = Files.list(PersonaFixtureGenerator.DIRECTORY)) {
            assertThat(files.map(f -> f.getFileName().toString()).filter(n -> n.matches("p-.*\\.json")))
                    .as("no fixture without a generator entry")
                    .containsExactlyInAnyOrderElementsOf(generated.keySet().stream().map(id -> id + ".json").toList());
        }
        for (Map.Entry<String, String> entry : generated.entrySet()) {
            assertThat(Files.readAllBytes(file(entry.getKey())))
                    .as("%s differs from the generator output; rerun with -Dfine.fixtures.write=true if intended",
                            file(entry.getKey()))
                    .isEqualTo(entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Path file(String personaId) {
        return PersonaFixtureGenerator.DIRECTORY.resolve(personaId + ".json");
    }
}
