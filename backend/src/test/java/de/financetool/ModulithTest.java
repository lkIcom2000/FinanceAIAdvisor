package de.financetool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/** AD-12: module boundaries and the allowed dependency graph. */
class ModulithTest {

    /** The AD-12 graph. Every module may additionally use the open {@code shared} module. */
    static final Map<String, Set<String>> AD12_GRAPH = Map.ofEntries(
            Map.entry("shared", Set.of()),
            Map.entry("personas", Set.of()),
            Map.entry("bank", Set.of()),
            Map.entry("knowledge", Set.of()),
            Map.entry("audit", Set.of()),
            Map.entry("llm", Set.of()),
            Map.entry("profile", Set.of()),
            Map.entry("documents", Set.of("llm", "audit", "personas")),
            Map.entry("core", Set.of("bank", "knowledge", "audit", "documents")),
            Map.entry("actions", Set.of("core", "bank", "profile", "audit")),
            Map.entry("escalation", Set.of("audit")),
            Map.entry("advisory", Set.of("core", "profile", "knowledge", "llm", "audit", "escalation", "personas")),
            Map.entry("monitoring", Set.of("core", "actions", "profile", "escalation", "audit", "personas", "documents")),
            Map.entry("research", Set.of("advisory", "monitoring", "actions", "escalation", "profile", "bank",
                    "knowledge", "personas", "audit", "documents")));

    private final ApplicationModules modules = ApplicationModules.of(FinanceToolApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void theModulesAreExactlyTheAd12Modules() {
        Set<String> names = modules.stream().map(m -> m.getIdentifier().toString()).collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrderElementsOf(AD12_GRAPH.keySet());
    }

    @Test
    void declaredDependenciesEqualTheAd12Graph() throws ClassNotFoundException {
        Map<String, Set<String>> declared = new TreeMap<>();
        for (String module : AD12_GRAPH.keySet()) {
            ApplicationModule annotation = Class.forName("de.financetool." + module + ".package-info")
                    .getAnnotation(ApplicationModule.class);
            assertThat(annotation).as("@ApplicationModule on %s", module).isNotNull();
            Set<String> deps = new TreeSet<>(Arrays.asList(annotation.allowedDependencies()));
            deps.remove("shared");
            declared.put(module, deps);
        }
        Map<String, Set<String>> expected = new TreeMap<>();
        AD12_GRAPH.forEach((k, v) -> expected.put(k, new TreeSet<>(v)));
        assertThat(declared).isEqualTo(expected);
    }

    @Test
    void aDependencyOutsideTheDeclaredGraphFailsVerification() {
        // The fixture modules live in test sources, so include test classes in the import.
        ApplicationModules fixture = ApplicationModules.of("archfixture.modulith", location -> true);
        assertThatThrownBy(fixture::verify)
                .isInstanceOf(Violations.class)
                .hasMessageContaining("alpha")
                .hasMessageContaining("beta");
    }
}
