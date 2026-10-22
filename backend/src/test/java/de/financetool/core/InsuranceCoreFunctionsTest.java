package de.financetool.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.core.domain.CoverageFlag;
import de.financetool.core.domain.DuplicateCoverage;
import de.financetool.core.domain.Figure;
import de.financetool.core.domain.FigureInput;
import de.financetool.core.domain.InsuranceContract;
import de.financetool.core.domain.InsuranceContract.Cover;
import de.financetool.core.domain.InsurancePremium;
import de.financetool.core.domain.MissingEssentialCover;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pure insurance functions on the seeded persona fixtures (Story 2.5 I/O matrix). */
class InsuranceCoreFunctionsTest {

    static final PersonaFixtureCatalog CATALOG = new PersonaFixtureCatalog(Path.of("..", "data", "personas"));
    static final LocalDate START = LocalDate.of(2026, 10, 15);

    /** The persona's contracts as the gateway maps them. */
    static List<InsuranceContract> contracts(String personaId) {
        PersonaFixture fixture = CATALOG.fixture(new PersonaId(personaId));
        return fixture.contracts().stream()
                .map(c -> new InsuranceContract(c.id().toString(), Cover.valueOf(c.coverage()), c.policyholder(),
                        "CO_INSURED".equals(c.insuredRole()), c.premium(),
                        c.paymentInterval() == null ? null : switch (c.paymentInterval()) {
                            case "MONTHLY" -> 1;
                            case "QUARTERLY" -> 3;
                            case "ANNUAL" -> 12;
                            default -> throw new IllegalArgumentException(c.paymentInterval());
                        }, c.nextDueDate()))
                .toList();
    }

    static BigDecimal output(List<Figure> outputs, String key) {
        return outputs.stream().filter(f -> f.outputKey().equals(key)).findFirst().orElseThrow().value();
    }

    @Test
    void jonasPaysTwiceForLiabilityAndLacksOccupationalDisabilityCover() {
        List<InsuranceContract> jonas = contracts("p-jonas");
        DuplicateCoverage.Result duplicates = DuplicateCoverage.detect(jonas, "p-jonas");

        assertThat(duplicates.flags()).singleElement().satisfies(flag -> {
            assertThat(flag.kind()).isEqualTo(CoverageFlag.Kind.DUPLICATE_COVERAGE);
            assertThat(flag.cover()).isEqualTo(Cover.LIABILITY);
            assertThat(flag.cover().plainWords()).isEqualTo("liability");
            assertThat(flag.contractIds()).containsExactlyElementsOf(jonas.stream().map(InsuranceContract::id)
                    .toList());
        });
        assertThat(output(duplicates.computation().outputs(), DuplicateCoverage.OUTPUT_COUNT)).isEqualTo("1");
        assertThat(output(duplicates.computation().outputs(), duplicates.flags().getFirst().outputKey()))
                .isEqualTo("2");
        assertThat(duplicates.computation().inputs()).extracting(FigureInput::text).containsExactly(
                "Private liability insurance, policyholder Jonas",
                "Private liability insurance, co-insured on the policy of Parents of Jonas");
        assertThat(duplicates.computation().assumptions()).contains("Whether the family policy still covers you"
                + " depends on its conditions, such as having your own household.");

        MissingEssentialCover.Result missing = MissingEssentialCover.detect(jonas);
        assertThat(missing.flags()).extracting(CoverageFlag::cover).containsExactly(Cover.OCCUPATIONAL_DISABILITY);
        assertThat(output(missing.computation().outputs(), missing.flags().getFirst().outputKey())).isEqualTo("0");
    }

    @Test
    void timHasNoContractsAndMissesBothEssentialCovers() {
        List<InsuranceContract> tim = contracts("p-tim");
        assertThat(tim).isEmpty();

        assertThat(DuplicateCoverage.detect(tim, "p-tim").flags()).isEmpty();
        MissingEssentialCover.Result missing = MissingEssentialCover.detect(tim);
        assertThat(missing.flags()).extracting(CoverageFlag::kind).containsOnly(CoverageFlag.Kind.MISSING_COVER);
        assertThat(missing.flags()).extracting(CoverageFlag::cover)
                .containsExactly(Cover.LIABILITY, Cover.OCCUPATIONAL_DISABILITY);
        assertThat(missing.computation().assumptions()).isNotEmpty();
    }

    @Test
    void lenaHasNothingDuplicated() {
        DuplicateCoverage.Result duplicates = DuplicateCoverage.detect(contracts("p-lena"), "p-lena");
        assertThat(duplicates.flags()).isEmpty();
        assertThat(output(duplicates.computation().outputs(), DuplicateCoverage.OUTPUT_COUNT)).isEqualTo("0");
        assertThat(MissingEssentialCover.detect(contracts("p-lena")).flags()).extracting(CoverageFlag::cover)
                .containsExactly(Cover.OCCUPATIONAL_DISABILITY);
    }

    @Test
    void aRiesterContractIsNeitherComparedNorCountedAsCover() {
        InsuranceContract riester = new InsuranceContract("r1", Cover.RIESTER_PENSION, "Petra", false,
                new BigDecimal("45.00"), 1, LocalDate.of(2026, 11, 1));
        InsuranceContract riester2 = new InsuranceContract("r2", Cover.RIESTER_PENSION, "Petra", false,
                new BigDecimal("45.00"), 1, LocalDate.of(2026, 11, 1));
        assertThat(DuplicateCoverage.detect(List.of(riester, riester2), "p-petra").flags()).isEmpty();
        assertThat(MissingEssentialCover.detect(List.of(riester)).flags()).hasSize(2);

        assertThat(MissingEssentialCover.detect(contracts("p-petra")).flags()).extracting(CoverageFlag::cover)
                .containsExactly(Cover.OCCUPATIONAL_DISABILITY);
    }

    @Test
    void thePremiumsNextDueDateRollsForwardPastAsOf() {
        InsuranceContract car = contracts("p-lena").stream().filter(c -> c.cover() == Cover.CAR).findFirst()
                .orElseThrow();
        InsurancePremium.Result atStart = InsurancePremium.of(car, START);
        assertThat(atStart.nextDueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(atStart.computation().outputs().getFirst().value()).isEqualTo("620.00");
        assertThat(car.intervalWords()).isEqualTo("yearly");

        assertThat(InsurancePremium.of(car, LocalDate.of(2026, 11, 1)).nextDueDate())
                .isEqualTo(LocalDate.of(2027, 11, 1));
        assertThat(InsurancePremium.nextDueAfter(LocalDate.of(2026, 1, 31), 1, LocalDate.of(2026, 3, 5)))
                .isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(InsurancePremium.of(car, LocalDate.of(2026, 11, 5)).computation().assumptions()).isNotEmpty();
    }

    @Test
    void aContractWithoutPremiumHasNoPremiumFigure() {
        InsuranceContract family = contracts("p-jonas").stream().filter(InsuranceContract::coInsured).findFirst()
                .orElseThrow();
        assertThat(family.premium()).isNull();
        assertThat(family.intervalWords()).isNull();
        assertThatThrownBy(() -> InsurancePremium.of(family, START)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identicalInputsGiveIdenticalResults() {
        assertThat(DuplicateCoverage.detect(contracts("p-jonas"), "p-jonas"))
                .isEqualTo(DuplicateCoverage.detect(contracts("p-jonas"), "p-jonas"));
    }
}
