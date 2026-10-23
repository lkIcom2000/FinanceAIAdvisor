package de.financetool.advisory.adapter;

import de.financetool.advisory.domain.EntityRegistry;
import de.financetool.advisory.domain.ProfileCompleteness;
import de.financetool.shared.AppHome;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The classifier's inputs that are no turn data: the entity registry and the profile-completeness port. */
@Configuration(proxyBeanMethods = false)
class ClassificationConfiguration {

    /** The registry file, under {@code data/rules/} so that the config fingerprint covers it. */
    static final String KNOWN_ENTITIES = "rules/known_entities.yaml";

    /** The registry from {@code data/rules/known_entities.yaml}; no Knowledge Base titles or texts until Epic 4. */
    @Bean
    EntityRegistry entityRegistry(AppHome appHome) {
        return new EntityRegistry(load(appHome.data().resolve(KNOWN_ENTITIES)), List.of(), List.of());
    }

    /** The stub until Story 6.1: every profile is incomplete, no missing fields known. */
    @Bean
    ProfileCompleteness profileCompleteness() {
        return ProfileCompleteness.INCOMPLETE;
    }

    /** The registry's lists; a missing or malformed file fails startup. */
    static Map<String, List<String>> load(Path file) {
        Object root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the entity registry " + file, e);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalStateException(file + " must be a map of lists");
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (value == null) {
                out.put(String.valueOf(key), List.of());
            } else if (value instanceof List<?> list) {
                out.put(String.valueOf(key), list.stream().map(String::valueOf).toList());
            } else {
                throw new IllegalStateException(file + ": " + key + " must be a list");
            }
        });
        return out;
    }
}
