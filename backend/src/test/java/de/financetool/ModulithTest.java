package de.financetool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import de.financetool.core.CoreGateway;
import de.financetool.research.application.ResetPersonaUseCase;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.NamedInterface;
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

    /** The named interface with the types CoreGateway returns (Epic 2 retro A2). */
    static final String CORE_RESULTS = "results";

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

    /** AD-3: chat never proposes an Action, so {@code advisory} has no dependency on {@code actions}. */
    @Test
    void advisoryDoesNotDependOnActions() {
        assertThat(AD12_GRAPH.get("advisory")).doesNotContain("actions");
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("de.financetool.advisory..")
                .should().dependOnClassesThat().resideInAPackage("de.financetool.actions..")
                .check(new com.tngtech.archunit.core.importer.ClassFileImporter()
                        .importPackages("de.financetool.advisory", "de.financetool.actions"));
    }

    @Test
    void declaredDependenciesEqualTheAd12Graph() throws ClassNotFoundException {
        Map<String, Set<String>> declared = new TreeMap<>();
        for (String module : AD12_GRAPH.keySet()) {
            ApplicationModule annotation = Class.forName("de.financetool." + module + ".package-info")
                    .getAnnotation(ApplicationModule.class);
            assertThat(annotation).as("@ApplicationModule on %s", module).isNotNull();
            List<String> allowed = Arrays.asList(annotation.allowedDependencies());
            if (allowed.contains("core")) {
                assertThat(allowed).as("%s may call CoreGateway, so it needs its result types", module)
                        .contains("core :: " + CORE_RESULTS);
            }
            Set<String> deps = allowed.stream().map(d -> d.split(" :: ")[0]).collect(Collectors.toCollection(TreeSet::new));
            deps.remove("shared");
            declared.put(module, deps);
        }
        Map<String, Set<String>> expected = new TreeMap<>();
        AD12_GRAPH.forEach((k, v) -> expected.put(k, new TreeSet<>(v)));
        assertThat(declared).isEqualTo(expected);
    }

    /** Persona reset (AD-12): every owner runs before the modules it depends on; audit, knowledge, llm never. */
    @Test
    void theResetOrderRespectsTheModuleGraph() {
        List<String> order = ResetPersonaUseCase.RESET_ORDER;
        for (int i = 0; i < order.size(); i++) {
            for (String dependency : AD12_GRAPH.get(order.get(i))) {
                if (order.contains(dependency)) {
                    assertThat(order.indexOf(dependency)).as(order.get(i) + " depends on " + dependency)
                            .isGreaterThan(i);
                }
            }
        }
        assertThat(order).doesNotContain("audit", "knowledge", "llm", "research", "shared")
                .containsAll(AD12_GRAPH.keySet().stream()
                        .filter(m -> !Set.of("audit", "knowledge", "llm", "research", "shared").contains(m)).toList());
    }

    /**
     * Epic 2 retro A2: every Core type a CoreGateway signature reaches is exposed (base package or {@code results}),
     * and {@code results} exposes nothing else, so the Core's functions stay behind the gateway.
     */
    @Test
    void theResultsInterfaceIsExactlyWhatTheGatewayReturns() {
        Set<Class<?>> reached = new LinkedHashSet<>();
        for (Method method : CoreGateway.class.getDeclaredMethods()) {
            collectCoreTypes(method.getGenericReturnType(), reached);
        }
        Set<String> needed = reached.stream()
                .filter(c -> !c.getPackageName().equals(CoreGateway.class.getPackageName()))
                .map(Class::getName).collect(Collectors.toCollection(TreeSet::new));
        NamedInterface results = modules.getModuleByName("core").orElseThrow().getNamedInterfaces()
                .getByName(CORE_RESULTS).orElseThrow();
        Set<String> exposed = results.asJavaClasses().map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(exposed).isEqualTo(needed);
    }

    private static void collectCoreTypes(Type type, Set<Class<?>> reached) {
        if (type instanceof ParameterizedType parameterized) {
            collectCoreTypes(parameterized.getRawType(), reached);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectCoreTypes(argument, reached);
            }
        } else if (type instanceof Class<?> c && c.getName().startsWith("de.financetool.core.") && reached.add(c)) {
            if (c.isRecord()) {
                for (RecordComponent component : c.getRecordComponents()) {
                    collectCoreTypes(component.getGenericType(), reached);
                }
            }
        }
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
