package de.financetool;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/**
 * ArchUnit rules for AD-1 (pure domain) and AD-8 (no wall clock in domain or application code).
 * Shared by the production check and the negative fixture tests.
 */
final class ArchitectureRules {

    private static final Set<String> WALL_CLOCK_FACTORIES = Set.of("systemDefaultZone", "systemUTC");

    static final DescribedPredicate<JavaMethodCall> WALL_CLOCK_READ = DescribedPredicate.describe(
            "a wall-clock read (java.time *.now(), Clock.systemDefaultZone()/systemUTC(), System.currentTimeMillis())",
            call -> {
                String owner = call.getTargetOwner().getName();
                String name = call.getName();
                return (name.equals("now") && owner.startsWith("java.time."))
                        || (owner.equals("java.time.Clock") && WALL_CLOCK_FACTORIES.contains(name))
                        || (owner.equals("java.lang.System") && name.equals("currentTimeMillis"));
            });

    static final ArchRule DOMAIN_IS_PURE = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..")
            .because("AD-1: the domain is plain Java without frameworks or I/O");

    static final ArchRule NO_WALL_CLOCK = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().callMethodWhere(WALL_CLOCK_READ)
            .because("AD-8: domain time comes only from the persona's simulated clock")
            .allowEmptyShould(true);

    private ArchitectureRules() {
    }
}
