package de.financetool;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import de.financetool.personas.adapter.PersonaRepository;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaScope;
import java.util.Set;

/**
 * ArchUnit rules for AD-1 (pure domain), AD-8 (no wall clock in domain or application code), AD-11 (persona scope),
 * AD-21 (use cases own the transaction), AD-22 (configuration through the RequestContext) and the bank write path
 * (Story 2.1: only {@code BankPort} and persona reset write bank tables).
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
            .should().onlyDependOnClassesThat(resideInAnyPackage("java..", "..domain..")
                    .or(type(org.springframework.modulith.NamedInterface.class)))
            .because("AD-1: the domain is plain Java without frameworks or I/O (the Core's result types carry only "
                    + "the Modulith named-interface marker)");

    static final ArchRule NO_WALL_CLOCK = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().callMethodWhere(WALL_CLOCK_READ)
            .because("AD-8: domain time comes only from the persona's simulated clock")
            .allowEmptyShould(true);

    static final ArchRule PERSONA_SCOPE_ONLY_FROM_PRINCIPAL = noClasses()
            .that().doNotHaveFullyQualifiedName("de.financetool.shared.web.PersonaScopeResolver")
            .should().callConstructor(de.financetool.shared.PersonaScope.class, de.financetool.shared.domain.PersonaId.class)
            .because("AD-11: the persona of a request comes only from the authenticated principal");

    /** Jackson and data binding build records reflectively, which the constructor rule above cannot see. */
    static final ArchRule NO_PERSONA_SCOPE_FIELDS = noFields()
            .should().haveRawType(PersonaScope.class)
            .because("AD-11: as a field of a request DTO a PersonaScope would be bound from client data");

    static final ArchRule PERSONA_SCOPE_NOT_BOUND_FROM_REQUEST = codeUnits()
            .should(notBindAPersonaScopeFromTheRequest())
            .because("AD-11: only PersonaScopeResolver may supply a PersonaScope parameter")
            .allowEmptyShould(true);

    /** Raw-id persona reads exist for the login lookup and the scoped default methods only. */
    static final ArchRule RAW_PERSONA_ID_READS_ONLY_FOR_LOGIN = noClasses()
            .that().doNotHaveFullyQualifiedName("de.financetool.personas.application.AccountDetailsService")
            .and().doNotBelongToAnyOf(PersonaRepository.class, PersonaStateRepository.class)
            .should().callMethod(PersonaRepository.class, "findById", String.class)
            .orShould().callMethod(PersonaStateRepository.class, "findByPersonaId", String.class)
            .because("AD-11: persona-scoped reads take a PersonaScope, never a raw id");

    /** Configuration beans that reach services only through the RequestContext (AD-22). */
    static final ArchRule CONFIG_ONLY_THROUGH_REQUEST_CONTEXT = fields()
            .that().haveRawType(de.financetool.shared.config.AgentConfiguration.class)
            .or().haveRawType(de.financetool.shared.config.AutonomyThresholds.class)
            .or().haveRawType(de.financetool.shared.config.ConfigFingerprint.class)
            .should().beDeclaredIn(de.financetool.shared.RequestContext.class)
            .orShould().beDeclaredIn("de.financetool.personas.application.RequestContextService")
            // The model tag per role is infrastructure binding (AD-5, AD-18), resolved by the adapter itself.
            .orShould().beDeclaredIn("de.financetool.llm.adapter.OllamaLlmAdapter")
            .because("AD-22: services receive configuration through the RequestContext, never by injection");

    private static ArchCondition<JavaCodeUnit> notBindAPersonaScopeFromTheRequest() {
        return new ArchCondition<>("not bind a PersonaScope parameter with a Spring web binding annotation") {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                for (JavaParameter parameter : codeUnit.getParameters()) {
                    boolean bound = parameter.getRawType().isEquivalentTo(PersonaScope.class)
                            && parameter.getAnnotations().stream().anyMatch(annotation -> annotation.getRawType()
                                    .getPackageName().equals("org.springframework.web.bind.annotation"));
                    if (bound) {
                        events.add(SimpleConditionEvent.violated(codeUnit,
                                codeUnit.getFullName() + " binds PersonaScope from the request"));
                    }
                }
            }
        };
    }

    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    /** A {@code @Transactional} that may start a transaction: any propagation except {@code MANDATORY}. */
    static final DescribedPredicate<JavaAnnotation<?>> STARTS_A_TRANSACTION = DescribedPredicate.describe(
            "@Transactional without propagation MANDATORY",
            annotation -> annotation.getRawType().getName().equals(TRANSACTIONAL)
                    && annotation.get("propagation")
                            .map(value -> !((JavaEnumConstant) value).name().equals("MANDATORY"))
                            .orElse(true));

    static final DescribedPredicate<JavaClass> USE_CASE_OR_ITS_NESTED_TYPE = DescribedPredicate.describe(
            "a *UseCase or a type nested in one",
            type -> type.getSimpleName().endsWith("UseCase")
                    || type.getEnclosingClass().map(outer -> outer.getSimpleName().endsWith("UseCase")).orElse(false));

    static final ArchRule TRANSACTIONS_START_ONLY_IN_USE_CASES = methods()
            .that().areAnnotatedWith(STARTS_A_TRANSACTION)
            .should().beDeclaredInClassesThat().haveSimpleNameEndingWith("UseCase")
            .because("AD-21: *UseCase classes are the only transaction entry points; module services are MANDATORY")
            .allowEmptyShould(true);

    /** Class-level {@code @Transactional} also applies to non-writing methods (e.g. {@code module()}). */
    static final ArchRule TRANSACTIONAL_ON_METHODS_OUTSIDE_USE_CASES = classes()
            .that().areAnnotatedWith(TRANSACTIONAL)
            .should().haveSimpleNameEndingWith("UseCase")
            .because("AD-21: module services annotate their writing methods with MANDATORY, not the whole class")
            .allowEmptyShould(true);

    static final ArchRule NO_ASYNC_OR_TRANSACTIONAL_EVENTS = noMethods()
            .should().beAnnotatedWith("org.springframework.scheduling.annotation.Async")
            .orShould().beAnnotatedWith("org.springframework.transaction.event.TransactionalEventListener")
            .orShould().beAnnotatedWith("org.springframework.modulith.events.ApplicationModuleListener")
            .because("AD-21: one use case runs in one transaction; only synchronous @EventListener may break cycles");

    static final DescribedPredicate<JavaMethodCall> BANK_TABLE_WRITE = DescribedPredicate.describe(
            "a write method (insert*/delete*/update*/save*) of a bank repository in de.financetool.bank.adapter",
            call -> call.getTargetOwner().getPackageName().equals("de.financetool.bank.adapter")
                    && call.getName().matches("(insert|delete|update|save).*"));

    static final DescribedPredicate<JavaClass> BANK_PORT_OR_RESET_IN_BANK = DescribedPredicate.describe(
            "a de.financetool.bank.application class implementing BankPort or PersonaResettable",
            type -> type.getPackageName().equals("de.financetool.bank.application")
                    && (type.isAssignableTo("de.financetool.bank.BankPort")
                            || type.isAssignableTo("de.financetool.shared.PersonaResettable")));

    static final ArchRule BANK_WRITES_ONLY_THROUGH_BANK_PORT = noClasses()
            .that(DescribedPredicate.not(BANK_PORT_OR_RESET_IN_BANK))
            .and().resideOutsideOfPackage("de.financetool.bank.adapter")
            .should().callMethodWhere(BANK_TABLE_WRITE)
            .because("Story 2.1: only bank.BankPort and the bank's persona reset change the bank tables");

    static final ArchRule CONTROLLERS_CALL_ONLY_USE_CASES = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat(JavaClass.Predicates.resideInAPackage("..application..")
                    .and(DescribedPredicate.not(USE_CASE_OR_ITS_NESTED_TYPE)))
            .because("AD-21: controllers never call module services directly");

    /** Price, holding and index data, and the modules monitoring must reach only through the CoreGateway. */
    static final DescribedPredicate<JavaClass> PRICE_DATA_OR_RAW_SOURCES = resideInAnyPackage(
            "de.financetool.bank..", "de.financetool.knowledge..")
            .or(DescribedPredicate.describe("WealthFigures, Investments or IndexReturns",
                    type -> Set.of("de.financetool.core.domain.WealthFigures", "de.financetool.core.domain.Investments",
                            "de.financetool.core.application.IndexReturns").contains(type.getName())
                            || type.getName().startsWith("de.financetool.core.domain.WealthFigures$")));

    static final ArchRule MONITORING_IS_PRICE_FREE = noClasses()
            .that().resideInAPackage("de.financetool.monitoring..")
            .should().dependOnClassesThat(PRICE_DATA_OR_RAW_SOURCES)
            .orShould().callMethodWhere(DescribedPredicate.describe("CoreGateway.wealth or investmentTotals",
                    call -> call.getTargetOwner().getName().equals("de.financetool.core.CoreGateway")
                            && Set.of("wealth", "investmentTotals").contains(call.getName())))
            .because("Story 8.2 (S9): no Trigger follows a market move, so monitoring reads no prices, holdings or"
                    + " index data, and it gets every figure from the CoreGateway");

    private ArchitectureRules() {
    }
}
