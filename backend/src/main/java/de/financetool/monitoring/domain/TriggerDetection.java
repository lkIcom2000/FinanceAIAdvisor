package de.financetool.monitoring.domain;

import de.financetool.actions.domain.ActionType;
import de.financetool.monitoring.domain.DetectedTrigger.PlannedAction;
import de.financetool.monitoring.domain.DetectedTrigger.PlannedEscalation;
import de.financetool.monitoring.domain.DetectedTrigger.PlannedNotification;
import de.financetool.monitoring.domain.MonitoringSnapshot.Announcement;
import de.financetool.monitoring.domain.MonitoringSnapshot.PremiumDue;
import de.financetool.monitoring.domain.MonitoringSnapshot.RuleChange;
import de.financetool.monitoring.domain.MonitoringSnapshot.Shortfall;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Finds the MVP Triggers in a {@link MonitoringSnapshot} (FR-30, FR-31): forecast shortfall, emergency fund below
 * target, Duplicate Coverage, Rule Parameter changes and announced rule changes. Pure and deterministic: the same
 * snapshot and date give the same Triggers in the same order.
 */
public final class TriggerDetection {

    /** The subject of a Trigger that has none, e.g. an emergency fund without an overnight deposit. */
    public static final String NO_SUBJECT = "none";
    /** The subject of an ECB decision's Trigger: one per decision date, whichever key rates it changed. */
    public static final String ECB_SUBJECT = "ecb_key_rates";
    static final String SUBSIDY_PREFIX = "riester_";
    static final String ECB_PREFIX = "ecb_";
    static final String SUBSIDY_TOPIC = "subsidy";
    static final int ANNUAL_MONTHS = 12;

    /** Reason codes of an announcement's case. */
    public static final String NO_SOURCE = "no_source";
    public static final String NO_VALID_RULE_PARAMETER = "no_valid_rule_parameter";

    /**
     * The detection settings.
     *
     * @param ruleChangeDays how far back a Rule Parameter version may have started to count as a change
     */
    public record Rules(int ruleChangeDays) {

        public static final Rules DEFAULTS = new Rules(30);

        public Rules {
            if (ruleChangeDays < 1) {
                throw new IllegalArgumentException("ruleChangeDays must be positive");
            }
        }

        /** The day before the first day a change may start on. */
        public LocalDate changesAfter(LocalDate asOf) {
            return asOf.minusDays(ruleChangeDays);
        }
    }

    private TriggerDetection() {
    }

    public static List<DetectedTrigger> detect(MonitoringSnapshot snapshot, LocalDate asOf, Rules rules) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(rules, "rules");
        List<DetectedTrigger> triggers = new ArrayList<>();
        if (snapshot.shortfall() != null) {
            triggers.add(shortfall(snapshot, snapshot.shortfall()));
        }
        if (snapshot.emergencyFund().value().compareTo(snapshot.emergencyFundTarget().value()) < 0) {
            triggers.add(emergencyFund(snapshot, asOf));
        }
        snapshot.duplicates().stream()
                .sorted(Comparator.comparing(MonitoringSnapshot.Duplicate::cover))
                .map(d -> new DetectedTrigger(TriggerType.DUPLICATE_COVERAGE, d.cover(),
                        String.valueOf(asOf.getYear()), null, null,
                        List.of(note(NotificationOutcome.RECOMMENDED, NotificationTemplates.duplicateCoverage(d),
                                d.flag()))))
                .forEach(triggers::add);
        triggers.addAll(ruleChanges(snapshot, asOf, rules));
        snapshot.announcements().stream()
                .filter(a -> escalates(a, snapshot, asOf))
                .sorted(Comparator.comparing(Announcement::id))
                .map(TriggerDetection::announcement)
                .forEach(triggers::add);
        return List.copyOf(triggers);
    }

    private static DetectedTrigger shortfall(MonitoringSnapshot snapshot, Shortfall shortfall) {
        List<PlannedNotification> notes = new ArrayList<>();
        PlannedAction action = null;
        List<SourcedNumber> cited = List.of(shortfall.firstBelowZero(), shortfall.lowest());
        if (snapshot.overnightAccountId() != null) {
            BigDecimal amount = shortfall.lowest().value().negate();
            action = new PlannedAction(ActionType.SHORTFALL_TRANSFER, amount, snapshot.overnightAccountId(),
                    snapshot.currentAccountId());
            notes.add(note(NotificationOutcome.ACTION, NotificationTemplates.shortfallTransfer(shortfall, amount),
                    cited).withDone(NotificationTemplates.shortfallTransferDone(shortfall, amount)));
        } else {
            notes.add(note(NotificationOutcome.INFORMED, NotificationTemplates.shortfallWithoutOvernight(shortfall),
                    cited));
        }
        snapshot.annualPremiums().stream()
                .filter(p -> !p.nextDueDate().isAfter(shortfall.date()))
                .min(Comparator.comparing(PremiumDue::nextDueDate).thenComparing(PremiumDue::contractId))
                .ifPresent(p -> notes.add(note(NotificationOutcome.RECOMMENDED,
                        NotificationTemplates.payPremiumMonthly(p), p.premium())));
        return new DetectedTrigger(TriggerType.FORECAST_SHORTFALL, snapshot.currentAccountId().toString(),
                shortfall.date().toString(), action, null, notes);
    }

    private static DetectedTrigger emergencyFund(MonitoringSnapshot snapshot, LocalDate asOf) {
        SourcedNumber fund = snapshot.emergencyFund();
        SourcedNumber target = snapshot.emergencyFundTarget();
        String subject = snapshot.overnightAccountId() == null ? NO_SUBJECT
                : snapshot.overnightAccountId().toString();
        String period = YearMonth.from(asOf).toString();
        List<SourcedNumber> cited = List.of(fund, target);
        if (snapshot.overnightAccountId() == null) {
            return informed(TriggerType.EF_BELOW_TARGET, subject, period,
                    NotificationTemplates.emergencyFundWithoutOvernight(fund, target), cited);
        }
        if (snapshot.shortfall() != null) {
            return informed(TriggerType.EF_BELOW_TARGET, subject, period,
                    NotificationTemplates.emergencyFundWhileShortfall(fund, target), cited);
        }
        SourcedNumber topUp = Objects.requireNonNull(snapshot.topUp(), "the top-up of a fund below target");
        if (topUp.value().signum() <= 0) {
            return informed(TriggerType.EF_BELOW_TARGET, subject, period,
                    NotificationTemplates.emergencyFundNothingLeftOver(fund, target),
                    Stream.concat(cited.stream(), Stream.of(topUp)).toList());
        }
        PlannedAction action = new PlannedAction(ActionType.EF_TOP_UP, topUp.value(), snapshot.currentAccountId(),
                snapshot.overnightAccountId());
        return new DetectedTrigger(TriggerType.EF_BELOW_TARGET, subject, period, action, null,
                List.of(note(NotificationOutcome.ACTION,
                        NotificationTemplates.emergencyFundTopUp(fund, target, topUp.value()),
                        List.of(fund, target, topUp))
                        .withDone(NotificationTemplates.emergencyFundTopUpDone(fund, target, topUp.value()))));
    }

    private static List<DetectedTrigger> ruleChanges(MonitoringSnapshot snapshot, LocalDate asOf, Rules rules) {
        LocalDate after = rules.changesAfter(asOf);
        List<RuleChange> recent = snapshot.ruleChanges().stream()
                .filter(c -> c.validFrom().isAfter(after) && !c.validFrom().isAfter(asOf))
                .sorted(Comparator.comparing(RuleChange::validFrom).thenComparing(RuleChange::key))
                .toList();
        List<DetectedTrigger> triggers = new ArrayList<>();
        if (snapshot.netIncome().value().signum() > 0) {
            recent.stream()
                    .filter(c -> c.key().startsWith(SUBSIDY_PREFIX))
                    .map(c -> new DetectedTrigger(TriggerType.RULE_CHANGE, c.key(), c.version(), null, null,
                            List.of(note(NotificationOutcome.RECOMMENDED, NotificationTemplates.subsidyRuleChange(c),
                                    cited(c)))))
                    .forEach(triggers::add);
        }
        if (snapshot.overnightAccountId() != null) {
            Map<LocalDate, List<RuleChange>> decisions = new TreeMap<>();
            recent.stream()
                    .filter(c -> c.key().startsWith(ECB_PREFIX))
                    .forEach(c -> decisions.computeIfAbsent(c.validFrom(), d -> new ArrayList<>()).add(c));
            decisions.forEach((validFrom, changes) -> triggers.add(informed(TriggerType.RULE_CHANGE, ECB_SUBJECT,
                    validFrom.toString(), NotificationTemplates.ecbRateChange(validFrom, changes),
                    changes.stream().flatMap(c -> cited(c).stream()).toList())));
        }
        return triggers;
    }

    private static boolean escalates(Announcement announcement, MonitoringSnapshot snapshot, LocalDate asOf) {
        return SUBSIDY_TOPIC.equals(announcement.topic())
                && snapshot.netIncome().value().signum() > 0
                && !announcement.announcedOn().isAfter(asOf)
                && !asOf.isAfter(announcement.effectiveFrom())
                && (!announcement.hasSource() || !announcement.rulesValid());
    }

    private static DetectedTrigger announcement(Announcement announcement) {
        List<String> reasons = new ArrayList<>();
        if (!announcement.hasSource()) {
            reasons.add(NO_SOURCE);
        }
        if (!announcement.rulesValid()) {
            reasons.add(NO_VALID_RULE_PARAMETER);
        }
        return new DetectedTrigger(TriggerType.RULE_ANNOUNCEMENT, announcement.id(),
                announcement.effectiveFrom().toString(), null,
                new PlannedEscalation(NotificationTemplates.announcementCaseSummary(announcement), reasons),
                List.of(note(NotificationOutcome.ESCALATED, NotificationTemplates.announcementEscalated(announcement),
                        List.of())));
    }

    private static List<SourcedNumber> cited(RuleChange change) {
        return change.previous() == null ? List.of(change.value()) : List.of(change.value(), change.previous());
    }

    private static DetectedTrigger informed(TriggerType type, String subject, String period,
            NotificationTemplates.Text text, List<SourcedNumber> cited) {
        return new DetectedTrigger(type, subject, period, null, null,
                List.of(note(NotificationOutcome.INFORMED, text, cited)));
    }

    private static PlannedNotification note(NotificationOutcome outcome, NotificationTemplates.Text text,
            SourcedNumber cited) {
        return note(outcome, text, List.of(cited));
    }

    private static PlannedNotification note(NotificationOutcome outcome, NotificationTemplates.Text text,
            List<SourcedNumber> cited) {
        return new PlannedNotification(outcome, text.title(), text.body(),
                cited.stream().map(SourcedNumber::provId).toList(), text.question());
    }
}
