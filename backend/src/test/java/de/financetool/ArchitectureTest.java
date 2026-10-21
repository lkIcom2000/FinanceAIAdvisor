package de.financetool;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.financetool");

    @Test
    void domainIsPure() {
        ArchitectureRules.DOMAIN_IS_PURE.check(PRODUCTION);
    }

    @Test
    void noWallClockInDomainOrApplication() {
        ArchitectureRules.NO_WALL_CLOCK.check(PRODUCTION);
    }

    @Test
    void personaScopeIsCreatedOnlyFromThePrincipal() {
        ArchitectureRules.PERSONA_SCOPE_ONLY_FROM_PRINCIPAL.check(PRODUCTION);
    }

    @Test
    void personaScopeIsNeverBoundFromRequestData() {
        ArchitectureRules.NO_PERSONA_SCOPE_FIELDS.check(PRODUCTION);
        ArchitectureRules.PERSONA_SCOPE_NOT_BOUND_FROM_REQUEST.check(PRODUCTION);
    }

    @Test
    void rawPersonaIdReadsOnlyForLogin() {
        ArchitectureRules.RAW_PERSONA_ID_READS_ONLY_FOR_LOGIN.check(PRODUCTION);
    }

    @Test
    void configurationReachesServicesOnlyThroughTheRequestContext() {
        ArchitectureRules.CONFIG_ONLY_THROUGH_REQUEST_CONTEXT.check(PRODUCTION);
    }

    @Test
    void useCasesOwnTheTransaction() {
        ArchitectureRules.TRANSACTIONS_START_ONLY_IN_USE_CASES.check(PRODUCTION);
        ArchitectureRules.TRANSACTIONAL_ON_METHODS_OUTSIDE_USE_CASES.check(PRODUCTION);
        ArchitectureRules.NO_ASYNC_OR_TRANSACTIONAL_EVENTS.check(PRODUCTION);
        ArchitectureRules.CONTROLLERS_CALL_ONLY_USE_CASES.check(PRODUCTION);
    }

    @Test
    void bankTablesAreWrittenOnlyThroughBankPortOrReset() {
        ArchitectureRules.BANK_WRITES_ONLY_THROUGH_BANK_PORT.check(PRODUCTION);
    }

    /** Proves the rules actually fire, using deliberately broken classes in {@code archfixture}. */
    @Nested
    class NegativeFixtures {

        private final JavaClasses fixtures = new ClassFileImporter().importPackages("archfixture.rules");

        @Test
        void impureDomainClassIsAViolation() {
            EvaluationResult result = ArchitectureRules.DOMAIN_IS_PURE.evaluate(fixtures);
            assertThat(result.hasViolation()).isTrue();
            assertThat(result.getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("ImpureDomain") && line.contains("org.springframework"))
                    .noneMatch(line -> line.contains("PureDomain"));
        }

        @Test
        void personaScopeBuiltFromRequestDataIsAViolation() {
            EvaluationResult result = ArchitectureRules.PERSONA_SCOPE_ONLY_FROM_PRINCIPAL.evaluate(fixtures);
            assertThat(result.getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("ScopeFromRequest") && line.contains("PersonaScope.<init>"));
        }

        @Test
        void personaScopeBoundFromRequestDataIsAViolation() {
            assertThat(ArchitectureRules.NO_PERSONA_SCOPE_FIELDS.evaluate(fixtures).getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("ScopeFromRequest.bound"));
            assertThat(ArchitectureRules.PERSONA_SCOPE_NOT_BOUND_FROM_REQUEST.evaluate(fixtures).getFailureReport()
                    .getDetails())
                    .anyMatch(line -> line.contains("ScopeFromRequest.fromBody"));
        }

        @Test
        void rawPersonaIdReadIsAViolation() {
            assertThat(ArchitectureRules.RAW_PERSONA_ID_READS_ONLY_FOR_LOGIN.evaluate(fixtures).getFailureReport()
                    .getDetails())
                    .anyMatch(line -> line.contains("ScopeFromRequest") && line.contains("findById"));
        }

        @Test
        void injectedConfigurationIsAViolation() {
            EvaluationResult result = ArchitectureRules.CONFIG_ONLY_THROUGH_REQUEST_CONTEXT.evaluate(fixtures);
            assertThat(result.getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("ConfigReadingService.agentConfig"));
        }

        @Test
        void transactionsOutsideUseCasesAreViolations() {
            assertThat(ArchitectureRules.TRANSACTIONS_START_ONLY_IN_USE_CASES.evaluate(fixtures).getFailureReport()
                    .getDetails())
                    .anyMatch(line -> line.contains("TransactionalService.startsItsOwnTransaction"))
                    .noneMatch(line -> line.contains("FixtureUseCase"));
            assertThat(ArchitectureRules.TRANSACTIONAL_ON_METHODS_OUTSIDE_USE_CASES.evaluate(fixtures)
                    .getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("TransactionalService"));
            assertThat(ArchitectureRules.NO_ASYNC_OR_TRANSACTIONAL_EVENTS.evaluate(fixtures).getFailureReport()
                    .getDetails())
                    .anyMatch(line -> line.contains("TransactionalService"));
            assertThat(ArchitectureRules.CONTROLLERS_CALL_ONLY_USE_CASES.evaluate(fixtures).getFailureReport()
                    .getDetails())
                    .anyMatch(line -> line.contains("ServiceCallingController") && line.contains("TransactionalService"))
                    .noneMatch(line -> line.contains("FixtureUseCase"));
        }

        @Test
        void bankWritesOutsideBankPortAreViolations() {
            assertThat(ArchitectureRules.BANK_WRITES_ONLY_THROUGH_BANK_PORT.evaluate(fixtures).getFailureReport()
                    .getDetails())
                    .anyMatch(line -> line.contains("BankWritingService") && line.contains("deletePersonaRows"));
        }

        @Test
        void wallClockCallsAreViolations() {
            EvaluationResult result = ArchitectureRules.NO_WALL_CLOCK.evaluate(fixtures);
            assertThat(result.getFailureReport().getDetails())
                    .anyMatch(line -> line.contains("ClockInApplication") && line.contains("LocalDate.now()"))
                    .anyMatch(line -> line.contains("ClockInDomain") && line.contains("Instant.now()"))
                    .anyMatch(line -> line.contains("ClockInDomain") && line.contains("Clock.systemDefaultZone()"))
                    .noneMatch(line -> line.contains("PureDomain"));
        }
    }
}
