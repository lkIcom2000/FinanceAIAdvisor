package de.financetool.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixture.Contract;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Planted Ground Truth (FR-51) and the persona figures of PRD §2.3, checked with the test-side reference forecast
 * ({@link ReferenceForecast}). Later stories (Core forecast, Scenarios) assert the same values again.
 */
class PlantedGroundTruthTest {

    static final PersonaFixtureCatalog CATALOG = new PersonaFixtureCatalog(PersonaFixtureGenerator.DIRECTORY);

    static PersonaFixture fixture(String personaId) {
        return CATALOG.fixture(new PersonaId(personaId));
    }

    static Stream<Arguments> groundTruth() {
        return CATALOG.personaIds().stream().map(CATALOG::fixture)
                .flatMap(f -> f.plantedGroundTruth().stream().map(g -> Arguments.of(f.personaId(), g)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("groundTruth")
    void everyPlantedGroundTruthFollowsFromTheForecast(String personaId, PersonaFixture.PlantedGroundTruth truth) {
        PersonaFixture fixture = fixture(personaId);
        assertThat(new ReferenceForecast(fixture).balanceOn(truth.date(), truth.premiumOverride(), true))
                .isEqualByComparingTo(truth.expectedBalance());
        if (truth.premiumOverride() != null) {
            assertThat(new ReferenceForecast(fixture).premiumContracts().stream()
                    .filter(c -> c.nextDueDate().equals(truth.date())))
                    .as("the premium override applies to exactly one contract due on %s", truth.date()).hasSize(1);
        }
    }

    @Test
    void lenaDropsTo90BelowZeroOnTheFirstOfNovemberAnd214WithTheNewPremium() {
        ReferenceForecast lena = new ReferenceForecast(fixture("p-lena"));
        LocalDate nov1 = LocalDate.of(2026, 11, 1);

        assertThat(fixture("p-lena").simulatedDate()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(lena.balance(lena.current().id())).isEqualByComparingTo("1974.00");
        assertThat(lena.variableMonthly()).isEqualByComparingTo("930.00");
        assertThat(lena.balanceOn(nov1, null, true)).isEqualByComparingTo("-90.00");
        assertThat(lena.balanceOn(nov1, new BigDecimal("744.00"), true)).isEqualByComparingTo("-214.00");
        assertThat(lena.balanceOn(nov1, null, false)).as("fixed costs only").isEqualByComparingTo("421.00");
    }

    @Test
    void theShortfallPersonaIsAtLeast50BelowZeroOnTheDueDateWhileFixedCostsAloneStayPositive() {
        PersonaFixture shortfall = fixture("p-shortfall");
        ReferenceForecast forecast = new ReferenceForecast(shortfall);
        LocalDate due = LocalDate.of(2026, 11, 28);

        assertThat(shortfall.simulatedDate()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(forecast.balance(forecast.current().id())).isEqualByComparingTo("450.00");
        assertThat(forecast.variableMonthly()).isEqualByComparingTo("400.00");
        assertThat(fixedCosts(forecast)).isEqualByComparingTo("1800.00");
        assertThat(forecast.recurrences()).filteredOn(r -> r.category().equals("SALARY"))
                .singleElement().satisfies(salary -> {
                    assertThat(salary.amount()).isEqualByComparingTo("2300.00");
                    assertThat(salary.day()).isEqualTo(1);
                });
        assertThat(forecast.premiumContracts()).singleElement()
                .satisfies(car -> assertThat(car.nextDueDate()).isEqualTo(due));

        BigDecimal balance = forecast.balanceOn(due, null, true);
        assertThat(balance).isEqualByComparingTo("-82.04").isLessThanOrEqualTo(new BigDecimal("-50.00"));
        assertThat(forecast.balanceOn(due, null, false)).as("fixed costs only")
                .isEqualByComparingTo("330.00").isPositive();
    }

    @Test
    void personaFiguresMatchTheUserJourneys() {
        ReferenceForecast lena = new ReferenceForecast(fixture("p-lena"));
        assertThat(income(lena)).isEqualByComparingTo("2400.00");
        assertThat(lena.premiumContracts()).filteredOn(c -> c.coverage().equals("CAR")).singleElement()
                .satisfies(car -> {
                    assertThat(car.premium()).isEqualByComparingTo("620.00");
                    assertThat(car.paymentInterval()).isEqualTo("ANNUAL");
                    assertThat(car.nextDueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
                });
        assertThat(accountTypes("p-lena")).contains("OVERNIGHT");

        PersonaFixture marcoFixture = fixture("p-marco");
        ReferenceForecast marco = new ReferenceForecast(marcoFixture);
        assertThat(income(marco)).isEqualByComparingTo("2100.00");
        assertThat(monthlyExpenses(marco)).isEqualByComparingTo("2000.00");
        assertThat(marco.balance(account(marcoFixture, "OVERNIGHT"))).as("emergency fund")
                .isEqualByComparingTo("300.00");

        PersonaFixture jonas = fixture("p-jonas");
        assertThat(jonas.contracts()).filteredOn(c -> c.coverage().equals("LIABILITY"))
                .extracting(Contract::insuredRole).containsExactlyInAnyOrder("POLICYHOLDER", "CO_INSURED");
        assertThat(jonas.contracts()).filteredOn(c -> c.insuredRole().equals("CO_INSURED")).singleElement()
                .satisfies(family -> assertThat(family.premium()).isNull());
        assertThat(jonas.savingsPlans()).singleElement().satisfies(plan -> {
            assertThat(plan.amount()).isEqualByComparingTo("100.00");
            assertThat(plan.assetClass()).isEqualTo("EQUITY_ETF");
        });
        assertThat(jonas.holdings()).hasSize(1);

        PersonaFixture petra = fixture("p-petra");
        assertThat(petra.contracts()).filteredOn(c -> c.coverage().equals("RIESTER_PENSION")).singleElement()
                .satisfies(riester -> {
                    assertThat(riester.premium()).isEqualByComparingTo("45.00");
                    assertThat(riester.paymentInterval()).isEqualTo("MONTHLY");
                });
        assertThat(new ReferenceForecast(petra).recurrences())
                .filteredOn(r -> r.category().equals("CHILD_BENEFIT"))
                .extracting(r -> r.amount().toPlainString()).containsExactly("259.00", "259.00");

        ReferenceForecast tim = new ReferenceForecast(fixture("p-tim"));
        assertThat(income(tim)).isEqualByComparingTo("1150.00");
        assertThat(income(tim).subtract(monthlyExpenses(tim))).as("monthly surplus")
                .isBetween(new BigDecimal("70.00"), new BigDecimal("90.00"));

        PersonaFixture harness = fixture("p-harness");
        assertThat(harness.simulatedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(accountTypes("p-harness")).containsExactly("CURRENT");
        assertThat(harness.contracts()).isEmpty();
    }

    /** Every complete month gives V, so any window of complete months does (spec Design Notes). */
    @Test
    void everyCompleteMonthHasExactlyTheCalibratedVariableSpending() {
        for (PersonaId personaId : CATALOG.personaIds()) {
            PersonaFixture fixture = CATALOG.fixture(personaId);
            ReferenceForecast forecast = new ReferenceForecast(fixture);
            BigDecimal v = forecast.variableMonthly();
            YearMonth last = YearMonth.from(fixture.simulatedDate());
            for (YearMonth m = YearMonth.of(2025, 7); m.isBefore(last); m = m.plusMonths(1)) {
                assertThat(forecast.variableSpending(m)).as("%s %s", personaId, m).isEqualByComparingTo(v);
            }
            BigDecimal proRata = v.multiply(BigDecimal.valueOf(fixture.simulatedDate().getDayOfMonth()))
                    .divide(BigDecimal.valueOf(last.lengthOfMonth()), 2, RoundingMode.HALF_UP);
            assertThat(forecast.variableSpending(last)).as("%s partial month", personaId)
                    .isEqualByComparingTo(proRata);
        }
    }

    private static BigDecimal income(ReferenceForecast forecast) {
        return forecast.recurrences().stream().map(ReferenceForecast.Recurrence::amount)
                .filter(a -> a.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal fixedCosts(ReferenceForecast forecast) {
        return forecast.recurrences().stream().filter(r -> !List.of("TRANSFER", "SAVINGS_PLAN").contains(r.category()))
                .map(ReferenceForecast.Recurrence::amount).filter(a -> a.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add).negate();
    }

    /** Fixed costs + variable spending + premiums per month (savings and own transfers excluded). */
    private static BigDecimal monthlyExpenses(ReferenceForecast forecast) {
        BigDecimal premiums = forecast.premiumContracts().stream()
                .map(c -> c.premium().divide(BigDecimal.valueOf(ReferenceForecast.intervalMonths(c)),
                        MathContext.DECIMAL128))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return fixedCosts(forecast).add(forecast.variableMonthly()).add(premiums).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<String> accountTypes(String personaId) {
        return fixture(personaId).accounts().stream().map(PersonaFixture.Account::type).toList();
    }

    private static java.util.UUID account(PersonaFixture fixture, String type) {
        return fixture.accounts().stream().filter(a -> a.type().equals(type)).findFirst().orElseThrow().id();
    }
}
