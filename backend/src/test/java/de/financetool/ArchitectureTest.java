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
