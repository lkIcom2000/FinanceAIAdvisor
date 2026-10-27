package de.financetool.research.application;

import de.financetool.shared.AppHome;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import de.financetool.shared.web.SecurityConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The study presets in {@code APP_HOME/data/study-presets/<id>.json} (FR-31), loaded once at startup. Startup fails on
 * an unknown field, an id that is not the file name, an unknown persona, a date before the persona's fixture date, or
 * a setup step: no setup step type exists yet (Mandates come with Epic 9, Document facts with Epic 10).
 */
@Component
@Profile(SecurityConfig.RESEARCH_PROFILE)
public class StudyPresets {

    public static final String DIRECTORY = "study-presets";

    /** One preset: the persona, its simulated date and the setup steps that run before monitoring. */
    public record Preset(String id, String title, String personaId, LocalDate simulatedDate,
            List<Map<String, Object>> setup) {

        public Preset {
            setup = setup == null ? List.of() : List.copyOf(setup);
        }

        public PersonaId persona() {
            return new PersonaId(personaId);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final Map<String, Preset> presets;

    @Autowired
    StudyPresets(AppHome appHome, PersonaFixtureCatalog fixtures) {
        this(appHome.data().resolve(DIRECTORY), fixtures);
    }

    /** Loads and validates every preset in {@code directory}; throws {@link IllegalStateException} on any error. */
    StudyPresets(Path directory, PersonaFixtureCatalog fixtures) {
        Map<String, Preset> loaded = new TreeMap<>();
        for (Path file : files(directory)) {
            Preset preset = parse(file);
            validate(file, preset, fixtures);
            loaded.put(preset.id(), preset);
        }
        this.presets = Map.copyOf(loaded);
    }

    public Optional<Preset> find(String presetId) {
        return Optional.ofNullable(presets.get(presetId));
    }

    List<String> ids() {
        return presets.keySet().stream().sorted().toList();
    }

    private static Preset parse(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), Preset.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Study preset " + file + " is not valid: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read study preset " + file, e);
        }
    }

    private static void validate(Path file, Preset preset, PersonaFixtureCatalog fixtures) {
        String name = file.getFileName().toString();
        require(preset.id() != null && name.equals(preset.id() + ".json"), file, "id must be the file name");
        require(preset.title() != null && !preset.title().isBlank(), file, "title is missing");
        require(preset.simulatedDate() != null, file, "simulatedDate is missing");
        PersonaId persona;
        try {
            persona = preset.persona();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Study preset " + file + ": personaId is not valid", e);
        }
        require(fixtures.personaIds().contains(persona), file, "no persona fixture " + persona.value());
        require(!preset.simulatedDate().isBefore(fixtures.fixture(persona).simulatedDate()), file,
                "simulatedDate is before the persona's fixture date " + fixtures.fixture(persona).simulatedDate());
        require(preset.setup().isEmpty(), file, "unknown setup step " + (preset.setup().isEmpty() ? ""
                : preset.setup().getFirst().get("type")) + " (no setup step type exists yet)");
    }

    private static void require(boolean condition, Path file, String violation) {
        if (!condition) {
            throw new IllegalStateException("Study preset " + file + ": " + violation);
        }
    }

    private static List<Path> files(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Study preset directory " + directory + " is missing");
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list study presets in " + directory, e);
        }
    }
}
