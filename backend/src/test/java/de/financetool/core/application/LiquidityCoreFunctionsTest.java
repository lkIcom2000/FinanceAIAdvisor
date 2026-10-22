package de.financetool.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.CashFlowForecast;
import de.financetool.core.domain.CoreComputation;
import de.financetool.core.domain.DataConfidence;
import de.financetool.core.domain.EmergencyFundTarget;
import de.financetool.core.domain.Figure;
import de.financetool.core.domain.FigureInput;
import de.financetool.core.domain.LedgerEntry;
import de.financetool.core.domain.RuleParameterValue;
import de.financetool.core.domain.ShortfallDetection;
import de.financetool.core.domain.VariableSpending;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.fixtures.PersonaFixture;
import de.financetool.shared.fixtures.PersonaFixtureCatalog;
import de.financetool.testing.FixtureSnapshots;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The pure liquidity functions on the seeded persona fixtures: they reproduce the Planted Ground Truth of Story 2.1
 * (Lena −90.00 on 1 Nov, p-shortfall −82.04 on 28 Nov) and the I/O matrix of Story 2.3.
 */
class LiquidityCoreFunctionsTest {

    static final PersonaFixtureCatalog CATALOG = new PersonaFixtureCatalog(Path.of("..", "data", "personas"));
    static final RuleParameterValue BAND = new RuleParameterValue(CashFlowForecast.BAND, new BigDecimal("0.15"), "1",
            LocalDate.of(2020, 1, 1), null);
    static final RuleParameterValue MONTHS_MIN = new RuleParameterValue(EmergencyFundTarget.MONTHS_MIN,
            new BigDecimal("3"), "1", LocalDate.of(2020, 1, 1), null);
    static final RuleParameterValue MONTHS_MAX = new RuleParameterValue(EmergencyFundTarget.MONTHS_MAX,
            new BigDecimal("6"), "1", LocalDate.of(2020, 1, 1), null);

    /** The forecast of a persona's fixture at {@code asOf}, computed as the gateway does. */
    record Run(PersonaFixture fixture, LocalDate asOf, SourcedNumber start, SourcedNumber variable,
            CashFlowForecast forecast, ShortfallDetection.Result shortfall) {
    }

    static Run run(String personaId, LocalDate asOf) {
        PersonaFixture fixture = CATALOG.fixture(new PersonaId(personaId));
        PersonaFixture.Account current = account(fixture, "CURRENT");
        SourcedNumber start = keyed(AccountBalance.of(current.openingDate(), current.openingBalance(),
                bookings(fixture, current.id(), asOf), asOf), AccountBalance.OUTPUT);
        List<LedgerEntry> entries = entries(fixture, current.id(), asOf);
        SourcedNumber variable = keyed(VariableSpending.monthly(entries, asOf), VariableSpending.OUTPUT);
        List<CashFlowForecast.Premium> premiums = BankMapping.premiums(FixtureSnapshots.of(fixture, asOf),
                current.id());
        CashFlowForecast forecast = CashFlowForecast.of(start, variable,
                CashFlowForecast.recurrences(entries, asOf), premiums, BAND, asOf);
        ShortfallDetection.Result shortfall = ShortfallDetection.detect(forecast,
                keyed(forecast.computation(), CashFlowForecast.OUTPUT_EXPECTED));
        return new Run(fixture, asOf, start, variable, forecast, shortfall);
    }

    static Run run(String personaId) {
        return run(personaId, CATALOG.fixture(new PersonaId(personaId)).simulatedDate());
    }

    @Test
    void lenaDropsTo90BelowZeroOnTheFirstOfNovember() {
        Run lena = run("p-lena");

        assertThat(lena.start().value()).isEqualByComparingTo("1974.00");
        assertThat(lena.variable().value()).isEqualByComparingTo("930.00");
        assertThat(lena.shortfall().shortfallDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(output(lena.shortfall().computation(), ShortfallDetection.OUTPUT_SHORTFALL))
                .isEqualByComparingTo("-90.00");
        assertThat(lena.shortfall().lowestDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(output(lena.shortfall().computation(), ShortfallDetection.OUTPUT_LOWEST))
                .isEqualByComparingTo("-90.00");
        assertThat(lena.shortfall().computation().outputs().getFirst().label())
                .isEqualTo("Forecast balance on 1 Nov 2026");
    }

    @Test
    void theForecastRunsFromTheDayAfterAsOfToTheSameDayThreeMonthsLaterAsARange() {
        CashFlowForecast forecast = run("p-lena").forecast();

        assertThat(forecast.points().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(forecast.horizonEnd()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(forecast.points()).hasSize(92).allSatisfy(p -> {
            assertThat(p.low()).isLessThanOrEqualTo(p.expected());
            assertThat(p.high()).isGreaterThanOrEqualTo(p.expected());
            assertThat(p.expected().scale()).isEqualTo(2);
        });
        assertThat(forecast.assumptions()).isNotEmpty();
        assertThat(forecast.computation().ruleParamVersions()).containsEntry(CashFlowForecast.BAND, "1");
        assertThat(forecast.computation().inputs()).filteredOn(i -> i.provId() != null)
                .extracting(FigureInput::label)
                .containsExactly("Current account balance on 15 Oct 2026", "Average monthly variable spending");
        // Salary moved to the 2nd (Story 2.3): the balance is positive again on 2 Nov.
        assertThat(forecast.points()).filteredOn(p -> p.date().equals(LocalDate.of(2026, 11, 2)))
                .singleElement().satisfies(p -> assertThat(p.expected()).isPositive());
    }

    @Test
    void theShortfallPersonaIs82BelowZeroOnTheDueDate() {
        Run shortfall = run("p-shortfall");

        assertThat(shortfall.shortfall().shortfallDate()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(output(shortfall.shortfall().computation(), ShortfallDetection.OUTPUT_SHORTFALL))
                .isEqualByComparingTo("-82.04");
    }

    @Test
    void everyPlantedGroundTruthWithoutOverrideIsTheExpectedForecastBalance() {
        for (PersonaId id : CATALOG.personaIds()) {
            PersonaFixture fixture = CATALOG.fixture(id);
            for (PersonaFixture.PlantedGroundTruth truth : fixture.plantedGroundTruth()) {
                if (truth.premiumOverride() != null) {
                    continue;
                }
                assertThat(run(id.value()).forecast().points())
                        .filteredOn(p -> p.date().equals(truth.date())).singleElement()
                        .satisfies(p -> assertThat(p.expected()).as(truth.id())
                                .isEqualByComparingTo(truth.expectedBalance()));
            }
        }
    }

    @Test
    void marcoHasNoShortfall() {
        ShortfallDetection.Result marco = run("p-marco").shortfall();

        assertThat(marco.hasShortfall()).isFalse();
        assertThat(marco.computation().outputs()).extracting(Figure::outputKey)
                .containsExactly(ShortfallDetection.OUTPUT_LOWEST);
        assertThat(output(marco.computation(), ShortfallDetection.OUTPUT_LOWEST)).isNotNegative();
    }

    @Test
    void aDateAfterTheStoredDueDateRollsThePremiumForward() {
        CashFlowForecast.Premium car = new CashFlowForecast.Premium("Car", new BigDecimal("620.00"), 12,
                LocalDate.of(2026, 11, 1));
        assertThat(CashFlowForecast.nextDueAfter(car, LocalDate.of(2026, 11, 5))).isEqualTo(LocalDate.of(2027, 11, 1));
        assertThat(CashFlowForecast.nextDueAfter(car, LocalDate.of(2026, 11, 1))).isEqualTo(LocalDate.of(2027, 11, 1));
        assertThat(CashFlowForecast.nextDueAfter(car, LocalDate.of(2026, 10, 31))).isEqualTo(LocalDate.of(2026, 11, 1));

        Run lena = run("p-lena", LocalDate.of(2026, 11, 5));
        assertThat(lena.forecast().horizonEnd()).isEqualTo(LocalDate.of(2027, 2, 5));
        assertThat(lena.forecast().computation().inputs()).extracting(FigureInput::text)
                .contains("−620.00 EUR, next due on 1 Nov 2027");
    }

    @Test
    void aForecastWithoutAssumptionsOrWithoutItsRangeFailsValidation() {
        CashFlowForecast valid = run("p-lena").forecast();
        CashFlowForecast.Point point = valid.points().getFirst();

        assertThatThrownBy(() -> new CashFlowForecast(valid.asOf(), valid.points(), List.of(), valid.computation()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("assumptions");
        List<CashFlowForecast.Point> noLow = new ArrayList<>(valid.points());
        noLow.set(0, new CashFlowForecast.Point(point.date(), null, point.expected(), point.high()));
        assertThatThrownBy(() -> new CashFlowForecast(valid.asOf(), noLow, valid.assumptions(), valid.computation()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("range");
        List<CashFlowForecast.Point> noHigh = new ArrayList<>(valid.points());
        noHigh.set(0, new CashFlowForecast.Point(point.date(), point.low(), point.expected(), null));
        assertThatThrownBy(() -> new CashFlowForecast(valid.asOf(), noHigh, valid.assumptions(), valid.computation()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CashFlowForecast(valid.asOf(), List.of(), valid.assumptions(),
                valid.computation())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theEmergencyFundTargetIsThreeToSixMonthsOfExpenses() {
        PersonaFixture marco = CATALOG.fixture(new PersonaId("p-marco"));
        LocalDate asOf = marco.simulatedDate();
        PersonaFixture.Account current = account(marco, "CURRENT");
        PersonaFixture.Account overnight = account(marco, "OVERNIGHT");
        SourcedNumber overnightBalance = keyed(AccountBalance.of(AccountBalance.Account.OVERNIGHT,
                overnight.openingDate(), overnight.openingBalance(), bookings(marco, overnight.id(), asOf), asOf),
                AccountBalance.OUTPUT);

        CoreComputation fund = EmergencyFundTarget.of(overnightBalance, entries(marco, current.id(), asOf), asOf,
                MONTHS_MIN, MONTHS_MAX);

        assertThat(output(fund, EmergencyFundTarget.OUTPUT_CURRENT)).isEqualByComparingTo("300.00");
        assertThat(output(fund, EmergencyFundTarget.OUTPUT_MONTHLY_EXPENSES)).isEqualByComparingTo("2000.00");
        assertThat(output(fund, EmergencyFundTarget.OUTPUT_TARGET)).isEqualByComparingTo("6000.00");
        assertThat(output(fund, EmergencyFundTarget.OUTPUT_TARGET_MAX)).isEqualByComparingTo("12000.00");
        assertThat(output(fund, EmergencyFundTarget.OUTPUT_MONTHS)).isEqualByComparingTo("3");
        assertThat(fund.ruleParamVersions()).containsExactlyInAnyOrderEntriesOf(
                Map.of(EmergencyFundTarget.MONTHS_MIN, "1", EmergencyFundTarget.MONTHS_MAX, "1"));
        assertThat(fund.inputs().getFirst().provId()).isEqualTo(overnightBalance.provId());
    }

    @Test
    void theEmergencyFundLeavesOutTransfersAndSavingsPlans() {
        LocalDate asOf = LocalDate.of(2026, 10, 15);
        List<LedgerEntry> entries = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate day = LocalDate.of(2025, 9, 10).plusMonths(month);
            entries.add(new LedgerEntry(day, new BigDecimal("-1000.00"), "RENT", "Rent", "rent", false));
            entries.add(new LedgerEntry(day, new BigDecimal("-500.00"), "TRANSFER", "Savings", "so", false));
            entries.add(new LedgerEntry(day, new BigDecimal("-100.00"), "SAVINGS_PLAN", "ETF", "etf", false));
            entries.add(new LedgerEntry(day, new BigDecimal("3000.00"), "SALARY", "Salary", "salary", false));
        }
        CoreComputation fund = EmergencyFundTarget.of(null, entries, asOf, MONTHS_MIN, MONTHS_MAX);

        assertThat(output(fund, EmergencyFundTarget.OUTPUT_MONTHLY_EXPENSES)).isEqualByComparingTo("1000.00");
        assertThat(output(fund, EmergencyFundTarget.OUTPUT_CURRENT)).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> EmergencyFundTarget.of(null, entries, asOf, MONTHS_MAX, MONTHS_MIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dataConfidenceAppliesTheConfiguredThresholds() {
        LocalDate asOf = LocalDate.of(2026, 10, 15);
        BigDecimal min = new BigDecimal("0.95");
        List<LedgerEntry> everyFiveDays = new ArrayList<>();
        for (LocalDate day = asOf; day.isAfter(asOf.minusDays(95)); day = day.minusDays(5)) {
            everyFiveDays.add(new LedgerEntry(day, new BigDecimal("-1.00"), "GROCERIES", "Shop", null, false));
        }
        DataConfidence.Result high = DataConfidence.of(everyFiveDays, asOf, min, 5);
        assertThat(high.high()).isTrue();
        assertThat(high.longestGapDays()).isEqualTo(5);
        assertThat(high.coverage()).isEqualByComparingTo("1");
        assertThat(high.toCheck().passed()).isTrue();
        assertThat(high.toCheck().id()).isEqualTo(DataConfidence.CHECK_ID);

        assertThat(DataConfidence.of(everyFiveDays, asOf, min, 4).high()).as("gap above the maximum").isFalse();
        assertThat(DataConfidence.of(everyFiveDays, asOf.plusDays(6), min, 5).high())
                .as("no bookings since the last one").isFalse();

        List<LedgerEntry> uncategorised = new ArrayList<>(everyFiveDays);
        uncategorised.set(0, new LedgerEntry(asOf, new BigDecimal("-1.00"), null, "Shop", null, false));
        DataConfidence.Result low = DataConfidence.of(uncategorised, asOf, min, 5);
        assertThat(low.coverage()).isLessThan(min);
        assertThat(low.high()).isFalse();
        assertThat(low.toCheck().passed()).isFalse();
        assertThat(DataConfidence.of(List.of(), asOf, min, 5).high()).isFalse();

        assertThat(DataConfidence.of(entries(CATALOG.fixture(new PersonaId("p-lena")), null,
                LocalDate.of(2026, 10, 15)), LocalDate.of(2026, 10, 15), min, 5).high())
                .as("the seeded fixture is complete").isTrue();
    }

    @Test
    void dataConfidenceCountsTheGapFromTheWindowStartToTheFirstBooking() {
        LocalDate asOf = LocalDate.of(2026, 10, 15);
        List<LedgerEntry> oneRecent = List.of(
                new LedgerEntry(asOf.minusDays(1), new BigDecimal("-1.00"), "GROCERIES", "Shop", null, false));
        DataConfidence.Result result = DataConfidence.of(oneRecent, asOf, new BigDecimal("0.95"), 5);

        assertThat(result.longestGapDays()).isEqualTo(88);
        assertThat(result.high()).isFalse();
    }

    @Test
    void aSpendingBandOutsideZeroToOneIsRefused() {
        Run lena = run("p-lena");
        for (String value : List.of("-0.01", "1", "1.5")) {
            RuleParameterValue band = new RuleParameterValue(CashFlowForecast.BAND, new BigDecimal(value), "1",
                    LocalDate.of(2020, 1, 1), null);
            assertThatThrownBy(() -> CashFlowForecast.of(lena.start(), lena.variable(), List.of(), List.of(), band,
                    lena.asOf())).as(value).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(CashFlowForecast.BAND);
        }
    }

    @Test
    void savingsPlanDebitsAreNoVariableSpending() {
        LocalDate asOf = LocalDate.of(2026, 10, 15);
        List<LedgerEntry> entries = new ArrayList<>();
        for (int month = 7; month <= 9; month++) {
            LocalDate day = LocalDate.of(2026, month, 10);
            entries.add(new LedgerEntry(day, new BigDecimal("-300.00"), "GROCERIES", "Shop", null, false));
            entries.add(new LedgerEntry(day, new BigDecimal("-100.00"), "SAVINGS_PLAN", "ETF", null, false));
            entries.add(new LedgerEntry(day, new BigDecimal("-50.00"), "TRANSFER", "Savings", null, false));
        }
        assertThat(output(VariableSpending.monthly(entries, asOf), VariableSpending.OUTPUT))
                .isEqualByComparingTo("300.00");
    }

    // ------------------------------------------------------------------------------------------------- helpers

    static PersonaFixture.Account account(PersonaFixture fixture, String type) {
        return fixture.accounts().stream().filter(a -> a.type().equals(type)).findFirst().orElseThrow();
    }

    static List<AccountBalance.Booking> bookings(PersonaFixture fixture, UUID accountId, LocalDate asOf) {
        return BankMapping.bookings(FixtureSnapshots.of(fixture, asOf), accountId);
    }

    /** The bookings of {@code accountId} (all accounts if {@code null}) up to {@code asOf}. */
    static List<LedgerEntry> entries(PersonaFixture fixture, UUID accountId, LocalDate asOf) {
        return BankMapping.entries(FixtureSnapshots.of(fixture, asOf), accountId);
    }

    static SourcedNumber keyed(CoreComputation computation, String outputKey) {
        Figure figure = computation.outputs().stream().filter(f -> f.outputKey().equals(outputKey)).findFirst()
                .orElseThrow();
        return new SourcedNumber("0123456789abcdef", figure.value(), figure.unit(), figure.label());
    }

    static BigDecimal output(CoreComputation computation, String outputKey) {
        return computation.outputs().stream().filter(f -> f.outputKey().equals(outputKey)).findFirst()
                .orElseThrow().value();
    }
}
