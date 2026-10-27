package de.financetool.research.application;

import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.actions.domain.RuleTexts;
import de.financetool.shared.AppHome;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import de.financetool.shared.web.SecurityConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * a setup step other than a {@code mandate} step with a known template and valid bounds (Document facts come with
 * Epic 10).
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

        /** The {@code mandate} setup steps in file order (the only step type, validated at startup). */
        public List<MandateStep> mandates() {
            return setup.stream().map(MandateStep::of).toList();
        }
    }

    /** A setup step that starts a rule: {@code {"type":"mandate","template":…,"maxPerAction":…,…}}. */
    public record MandateStep(RuleTemplate template, RuleBounds bounds) {

        static final Set<String> KEYS = Set.of("type", "template", "maxPerAction", "maxPerMonth", "floor");

        /** @throws IllegalArgumentException on a missing or unknown key, template or amount */
        static MandateStep of(Map<String, Object> step) {
            if (!step.keySet().equals(KEYS)) {
                throw new IllegalArgumentException("a mandate step has exactly the keys " + KEYS);
            }
            return new MandateStep(RuleTemplate.valueOf((String) step.get("template")),
                    new RuleBounds(amount(step, "maxPerAction"), amount(step, "maxPerMonth"), amount(step, "floor")));
        }

        private static BigDecimal amount(Map<String, Object> step, String key) {
            return new BigDecimal((String) step.get(key));
        }
    }

    static final String MANDATE = "mandate";

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
        Set<RuleTemplate> templates = new HashSet<>();
        for (Map<String, Object> step : preset.setup()) {
            require(MANDATE.equals(step.get("type")), file, "unknown setup step " + step.get("type"));
            try {
                MandateStep mandate = MandateStep.of(step);
                require(templates.add(mandate.template()), file, "two mandate steps for " + mandate.template());
                RuleTexts.problem(mandate.bounds()).ifPresent(problem -> require(false, file,
                        "mandate bounds are not valid: " + problem));
            } catch (IllegalArgumentException | NullPointerException | ClassCastException e) {
                throw new IllegalStateException("Study preset " + file + ": mandate step is not valid", e);
            }
        }
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
