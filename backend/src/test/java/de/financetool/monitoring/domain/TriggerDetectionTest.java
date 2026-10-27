package de.financetool.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.financetool.actions.domain.ActionType;
import de.financetool.monitoring.domain.MonitoringSnapshot.Announcement;
import de.financetool.monitoring.domain.MonitoringSnapshot.Duplicate;
import de.financetool.monitoring.domain.MonitoringSnapshot.PremiumDue;
import de.financetool.monitoring.domain.MonitoringSnapshot.RuleChange;
import de.financetool.monitoring.domain.MonitoringSnapshot.Shortfall;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Story 8.2: the pure Trigger detection over the I/O matrix (UJ-1, S4, S6, S9, S12a, S12b, S19). */
class TriggerDetectionTest {

    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    static final UUID CURRENT = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final UUID OVERNIGHT = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    static final Shortfall SHORTFALL = new Shortfall(LocalDate.of(2026, 11, 1), eur("p-short", "-40.00"),
            LocalDate.of(2026, 11, 3), eur("p-low", "-90.00"));
    static final PremiumDue ANNUAL = new PremiumDue("c-liability", "Private liability insurance",
            LocalDate.of(2026, 11, 1), eur("p-premium", "120.00"));

    /** A figure key for a readable test name. */
    static String id(String name) {
        return String.format("%016x", name.hashCode() & 0xffffffffL);
    }

    static SourcedNumber eur(String name, String value) {
        return new SourcedNumber(id(name), new BigDecimal(value), Unit.EUR, name);
    }

    static SourcedNumber fraction(String name, String value) {
        return new SourcedNumber(id(name), new BigDecimal(value), Unit.FRACTION, name);
    }

    /** A persona with net income 2,000, fund 1,000 of 3,000, a top-up of 400 and nothing else. */
    static MonitoringSnapshot snapshot(Shortfall shortfall, UUID overnight, String topUp, List<PremiumDue> premiums,
            List<Duplicate> duplicates, List<RuleChange> changes, List<Announcement> announcements) {
        return new MonitoringSnapshot(CURRENT, overnight, shortfall, eur("p-fund", "1000.00"),
                eur("p-target", "3000.00"), topUp == null ? null : eur("p-topup", topUp), eur("p-income", "2000.00"),
                premiums, duplicates, changes, announcements);
    }

    static MonitoringSnapshot quiet() {
        return new MonitoringSnapshot(CURRENT, OVERNIGHT, null, eur("p-fund", "9000.00"), eur("p-target", "3000.00"),
                null, eur("p-income", "2000.00"), List.of(), List.of(), List.of(), List.of());
    }

    static List<DetectedTrigger> detect(MonitoringSnapshot snapshot) {
        return TriggerDetection.detect(snapshot, OCT_15, TriggerDetection.Rules.DEFAULTS);
    }

    @Test
    void uj1AShortfallProposesATransferOfTheLowestBalanceAndThePremiumHintAndTheFundOnlyInforms() {
        List<DetectedTrigger> triggers = detect(snapshot(SHORTFALL, OVERNIGHT, null, List.of(ANNUAL), List.of(),
                List.of(), List.of()));

        assertThat(triggers).extracting(DetectedTrigger::type)
                .containsExactly(TriggerType.FORECAST_SHORTFALL, TriggerType.EF_BELOW_TARGET);
        DetectedTrigger shortfall = triggers.get(0);
        assertThat(shortfall.subjectId()).isEqualTo(CURRENT.toString());
        assertThat(shortfall.period()).isEqualTo("2026-11-01");
        assertThat(shortfall.action().type()).isEqualTo(ActionType.SHORTFALL_TRANSFER);
        assertThat(shortfall.action().amount()).isEqualByComparingTo("90.00");
        assertThat(shortfall.action().sourceAccountId()).isEqualTo(OVERNIGHT);
        assertThat(shortfall.action().targetAccountId()).isEqualTo(CURRENT);
        assertThat(shortfall.notifications()).extracting(DetectedTrigger.PlannedNotification::outcome)
                .containsExactly(NotificationOutcome.ACTION, NotificationOutcome.RECOMMENDED);
        assertThat(shortfall.notifications().get(0).body())
                .contains("−90.00 EUR", "90.00 EUR from your overnight deposit");
        assertThat(shortfall.notifications().get(0).citedProvIds()).containsExactly(id("p-short"), id("p-low"));
        assertThat(shortfall.notifications().get(1).body())
                .contains("Paying the premium monthly would avoid this every year.");
        assertThat(shortfall.notifications().get(1).citedProvIds()).containsExactly(id("p-premium"));

        DetectedTrigger fund = triggers.get(1);
        assertThat(fund.subjectId()).isEqualTo(OVERNIGHT.toString());
        assertThat(fund.period()).isEqualTo("2026-10");
        assertThat(fund.action()).isNull();
        assertThat(fund.notifications()).singleElement()
                .satisfies(n -> assertThat(n.outcome()).isEqualTo(NotificationOutcome.INFORMED));
    }

    @Test
    void aPremiumDueOnTheShortfallDateStillGivesTheHint() {
        PremiumDue sameDay = new PremiumDue("c-home", "Household contents insurance", SHORTFALL.date(),
                eur("p-home", "80"));
        DetectedTrigger shortfall = detect(snapshot(SHORTFALL, OVERNIGHT, null, List.of(sameDay), List.of(),
                List.of(), List.of())).get(0);
        assertThat(shortfall.notifications()).hasSize(2);
        assertThat(shortfall.notifications().get(1).body()).contains("due on 1 Nov 2026, no later than the day");
    }

    @Test
    void aPremiumDueAfterTheShortfallGivesNoHint() {
        PremiumDue later = new PremiumDue("c-car", "Car insurance", LocalDate.of(2026, 12, 1), eur("p-car", "300"));
        DetectedTrigger shortfall = detect(snapshot(SHORTFALL, OVERNIGHT, null, List.of(later), List.of(), List.of(),
                List.of())).get(0);
        assertThat(shortfall.notifications()).singleElement()
                .satisfies(n -> assertThat(n.outcome()).isEqualTo(NotificationOutcome.ACTION));
    }

    @Test
    void s4WithoutAShortfallTheFundIsToppedUpByTheCoreFigure() {
        DetectedTrigger fund = detect(snapshot(null, OVERNIGHT, "400.00", List.of(), List.of(), List.of(),
                List.of())).getFirst();

        assertThat(fund.type()).isEqualTo(TriggerType.EF_BELOW_TARGET);
        assertThat(fund.action().type()).isEqualTo(ActionType.EF_TOP_UP);
        assertThat(fund.action().amount()).isEqualByComparingTo("400.00");
        assertThat(fund.action().sourceAccountId()).isEqualTo(CURRENT);
        assertThat(fund.action().targetAccountId()).isEqualTo(OVERNIGHT);
        assertThat(fund.notifications()).singleElement().satisfies(n -> {
            assertThat(n.outcome()).isEqualTo(NotificationOutcome.ACTION);
            assertThat(n.citedProvIds()).containsExactly(id("p-fund"), id("p-target"), id("p-topup"));
        });
    }

    @Test
    void aZeroTopUpOrNoOvernightDepositOnlyInforms() {
        DetectedTrigger zero = detect(snapshot(null, OVERNIGHT, "0.00", List.of(), List.of(), List.of(),
                List.of())).getFirst();
        assertThat(zero.action()).isNull();
        assertThat(zero.notifications()).singleElement()
                .satisfies(n -> assertThat(n.outcome()).isEqualTo(NotificationOutcome.INFORMED));

        DetectedTrigger none = detect(snapshot(null, null, null, List.of(), List.of(), List.of(), List.of()))
                .getFirst();
        assertThat(none.subjectId()).isEqualTo(TriggerDetection.NO_SUBJECT);
        assertThat(none.action()).isNull();
    }

    @Test
    void s6ADuplicateCoverageIsRecommendedWithoutAnAction() {
        Duplicate liability = new Duplicate("LIABILITY", "liability", fraction("p-dup", "1"));
        List<DetectedTrigger> triggers = detect(new MonitoringSnapshot(CURRENT, OVERNIGHT, null,
                eur("p-fund", "9000"), eur("p-target", "3000"), null, eur("p-income", "2000"), List.of(),
                List.of(liability), List.of(), List.of()));

        assertThat(triggers).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(TriggerType.DUPLICATE_COVERAGE);
            assertThat(t.subjectId()).isEqualTo("LIABILITY");
            assertThat(t.period()).isEqualTo("2026");
            assertThat(t.action()).isNull();
            assertThat(t.notifications()).singleElement().satisfies(n -> {
                assertThat(n.outcome()).isEqualTo(NotificationOutcome.RECOMMENDED);
                assertThat(n.title()).isEqualTo("You may be paying twice for liability insurance");
                assertThat(n.citedProvIds()).containsExactly(id("p-dup"));
            });
        });
    }

    static RuleChange change(String key, String name, LocalDate from, SourcedNumber value, SourcedNumber previous) {
        return new RuleChange(key, from.toString(), from, name, value, previous);
    }

    static MonitoringSnapshot withRules(UUID overnight, String income, List<RuleChange> changes,
            List<Announcement> announcements) {
        return new MonitoringSnapshot(CURRENT, overnight, null, eur("p-fund", "9000"), eur("p-target", "3000"), null,
                eur("p-income", income), List.of(), List.of(), changes, announcements);
    }

    @Test
    void s12aARecentSubsidyRuleChangeIsRecommendedToAPersonaWithIncome() {
        RuleChange basic = change("riester_basic_allowance", "Riester basic allowance", LocalDate.of(2026, 10, 1),
                eur("p-new", "200"), eur("p-old", "175"));
        List<DetectedTrigger> triggers = detect(withRules(OVERNIGHT, "2000", List.of(basic), List.of()));

        assertThat(triggers).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(TriggerType.RULE_CHANGE);
            assertThat(t.subjectId()).isEqualTo("riester_basic_allowance");
            assertThat(t.period()).isEqualTo("2026-10-01");
            assertThat(t.notifications()).singleElement().satisfies(n -> {
                assertThat(n.outcome()).isEqualTo(NotificationOutcome.RECOMMENDED);
                assertThat(n.body()).contains("200.00 EUR from 1 Oct 2026 (before: 175.00 EUR)");
                assertThat(n.citedProvIds()).containsExactly(id("p-new"), id("p-old"));
            });
        });
        assertThat(detect(withRules(OVERNIGHT, "0.00", List.of(basic), List.of()))).isEmpty();
    }

    @Test
    void s19AnEcbDecisionIsOneInformativeNoteOnDepositRateAndOverdraftNeverInvestmentAdvice() {
        LocalDate decision = LocalDate.of(2026, 10, 8);
        List<RuleChange> ecb = List.of(
                change("ecb_deposit_facility_rate", "ECB deposit facility rate", decision,
                        fraction("p-dfr", "0.0175"), fraction("p-dfr-old", "0.02")),
                change("ecb_main_refinancing_rate", "ECB main refinancing rate", decision,
                        fraction("p-mro", "0.019"), fraction("p-mro-old", "0.0215")));
        List<DetectedTrigger> triggers = detect(withRules(OVERNIGHT, "2000", ecb, List.of()));

        assertThat(triggers).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(TriggerType.RULE_CHANGE);
            assertThat(t.subjectId()).isEqualTo(TriggerDetection.ECB_SUBJECT);
            assertThat(t.period()).isEqualTo("2026-10-08");
            assertThat(t.action()).isNull();
            assertThat(t.notifications()).singleElement().satisfies(n -> {
                assertThat(n.outcome()).isEqualTo(NotificationOutcome.INFORMED);
                assertThat(n.body()).contains("ECB deposit facility rate 2 % to 1.75 %", "overnight deposit",
                        "overdraft").doesNotContainIgnoringCase("buy").doesNotContainIgnoringCase("sell")
                        .doesNotContainIgnoringCase("invest");
                assertThat(n.citedProvIds())
                        .containsExactly(id("p-dfr"), id("p-dfr-old"), id("p-mro"), id("p-mro-old"));
            });
        });
        assertThat(detect(withRules(null, "2000", ecb, List.of()))).as("no overnight deposit").isEmpty();
    }

    @Test
    void s9OldOrUnrelatedRuleChangesAndOtherKeysGiveNoTrigger() {
        List<RuleChange> changes = List.of(
                change("riester_basic_allowance", "Riester basic allowance", LocalDate.of(2026, 9, 15),
                        eur("p-old-change", "200"), null),
                change("riester_basic_allowance", "Riester basic allowance", LocalDate.of(2026, 10, 16),
                        eur("p-future", "210"), null),
                change("projection_equity_mean_return", "projection equity mean return", LocalDate.of(2026, 10, 10),
                        fraction("p-return", "0.05"), null));
        assertThat(detect(withRules(OVERNIGHT, "2000", changes, List.of()))).isEmpty();
        assertThat(detect(quiet())).isEmpty();
    }

    @Test
    void s12bAnAnnouncedSubsidyWithoutRulesEscalatesWithoutAnAction() {
        Announcement reform = new Announcement("reform-2027", "subsidy", "The retirement savings reform",
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1), false, false);
        List<DetectedTrigger> triggers = detect(withRules(OVERNIGHT, "2000", List.of(), List.of(reform)));

        assertThat(triggers).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(TriggerType.RULE_ANNOUNCEMENT);
            assertThat(t.subjectId()).isEqualTo("reform-2027");
            assertThat(t.period()).isEqualTo("2027-01-01");
            assertThat(t.action()).isNull();
            assertThat(t.escalation().reasons()).containsExactly(TriggerDetection.NO_SOURCE,
                    TriggerDetection.NO_VALID_RULE_PARAMETER);
            assertThat(t.notifications()).singleElement()
                    .satisfies(n -> assertThat(n.outcome()).isEqualTo(NotificationOutcome.ESCALATED));
        });

        Announcement confirmed = new Announcement("reform-2027", "subsidy", "The retirement savings reform",
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 1, 1), true, true);
        Announcement notYet = new Announcement("later", "subsidy", "Later", LocalDate.of(2026, 10, 20),
                LocalDate.of(2027, 1, 1), false, false);
        Announcement past = new Announcement("past", "subsidy", "Past", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 1), false, false);
        Announcement tax = new Announcement("tax", "tax", "Tax", LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 1, 1), false, false);
        assertThat(detect(withRules(OVERNIGHT, "2000", List.of(), List.of(confirmed, notYet, past, tax))))
                .isEmpty();
        assertThat(detect(withRules(OVERNIGHT, "0.00", List.of(), List.of(reform)))).as("no net income").isEmpty();
        Announcement today = new Announcement("today", "subsidy", "Today", LocalDate.of(2026, 10, 1), OCT_15, false,
                false);
        assertThat(detect(withRules(OVERNIGHT, "2000", List.of(), List.of(today)))).as("effective on asOf")
                .singleElement().satisfies(t -> assertThat(t.type()).isEqualTo(TriggerType.RULE_ANNOUNCEMENT));
    }

    @Test
    void theSameSnapshotGivesTheSameTriggers() {
        MonitoringSnapshot snapshot = snapshot(SHORTFALL, OVERNIGHT, null, List.of(ANNUAL),
                List.of(new Duplicate("LIABILITY", "liability", fraction("p-dup", "1"))), List.of(), List.of());
        assertThat(detect(snapshot)).isEqualTo(detect(snapshot));
    }
}
