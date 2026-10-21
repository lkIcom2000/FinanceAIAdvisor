package de.financetool;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
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

    static final ArchRule PERSONA_SCOPE_ONLY_FROM_PRINCIPAL = noClasses()
            .that().doNotHaveFullyQualifiedName("de.financetool.shared.web.PersonaScopeResolver")
            .should().callConstructor(de.financetool.shared.PersonaScope.class, de.financetool.shared.domain.PersonaId.class)
            .because("AD-11: the persona of a request comes only from the authenticated principal");

    /** Configuration beans that reach services only through the RequestContext (AD-22). */
    static final ArchRule CONFIG_ONLY_THROUGH_REQUEST_CONTEXT = fields()
            .that().haveRawType(de.financetool.shared.config.AgentConfiguration.class)
            .or().haveRawType(de.financetool.shared.config.AutonomyThresholds.class)
            .or().haveRawType(de.financetool.shared.config.ConfigFingerprint.class)
            .should().beDeclaredIn(de.financetool.shared.RequestContext.class)
            .orShould().beDeclaredIn("de.financetool.personas.application.RequestContextService")
            .because("AD-22: services receive configuration through the RequestContext, never by injection");

    private ArchitectureRules() {
    }
}
