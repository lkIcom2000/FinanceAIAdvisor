package de.financetool.monitoring.application;

import de.financetool.actions.ActionProposal;
import de.financetool.actions.Actions;
import de.financetool.actions.ProposedAction;
import de.financetool.actions.domain.ActionState;
import de.financetool.audit.AuditEntry;
import de.financetool.monitoring.domain.NotificationTemplates;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.AccountIds;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.core.domain.RuleChangeFigures;
import de.financetool.escalation.EscalationCases;
import de.financetool.monitoring.Monitoring;
import de.financetool.monitoring.adapter.NotificationRepository;
import de.financetool.monitoring.adapter.TriggerRepository;
import de.financetool.monitoring.domain.DetectedTrigger;
import de.financetool.monitoring.domain.MonitoringSnapshot;
import de.financetool.monitoring.domain.NotificationOutcome;
import de.financetool.monitoring.domain.NotificationStatus;
import de.financetool.monitoring.domain.TriggerDetection;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One monitoring run (AD-16): gathers Core figures into a price-free {@link MonitoringSnapshot}, detects the
 * Triggers and writes the outputs of every new Trigger key. Actions go only through {@link Actions#propose}.
 */
@Service
class MonitoringService implements Monitoring {

    static final TriggerDetection.Rules RULES = TriggerDetection.Rules.DEFAULTS;
    private static final EvidenceOrigin ORIGIN = EvidenceOrigin.MONITORING;
    private static final int ANNUAL_MONTHS = 12;

    private final CoreGateway core;
    private final Actions actions;
    private final EscalationCases escalation;
    private final PersonaDirectory personas;
    private final TriggerRepository triggers;
    private final NotificationRepository notifications;
    private final AuditWriter audit;

    MonitoringService(CoreGateway core, Actions actions, EscalationCases escalation, PersonaDirectory personas,
            TriggerRepository triggers, NotificationRepository notifications, AuditWriter audit) {
        this.core = core;
        this.actions = actions;
        this.escalation = escalation;
        this.personas = personas;
        this.triggers = triggers;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Run run(RequestContext context, PersonaScope scope) {
        if (!scope.personaId().equals(context.personaId())) {
            throw new IllegalArgumentException("the scope is not the context's persona");
        }
        LocalDate asOf = context.asOf();
        String fixtureVersion = personas.fixtureVersion(scope);
        List<DetectedTrigger> detected = TriggerDetection.detect(snapshot(context), asOf, RULES);

        List<RunTrigger> runTriggers = new ArrayList<>();
        List<RunAction> runActions = new ArrayList<>();
        List<RunNotification> runNotifications = new ArrayList<>();
        for (DetectedTrigger trigger : detected) {
            UUID triggerId = UUID.randomUUID();
            if (!triggers.insertIfAbsent(scope, new TriggerRepository.Row(triggerId, trigger.type().name(),
                    trigger.subjectId(), trigger.period(), asOf))) {
                continue;
            }
            runTriggers.add(new RunTrigger(triggerId, trigger.type().name(), trigger.subjectId(), trigger.period()));

            UUID actionId = null;
            ActionState actionState = null;
            List<String> actionCriteria = List.of();
            if (trigger.action() != null) {
                DetectedTrigger.PlannedAction planned = trigger.action();
                ActionProposal proposal = actions.propose(context, scope, fixtureVersion,
                        new ProposedAction(planned.type(), planned.type().regulatoryClass(), planned.amount(),
                                planned.sourceAccountId(), planned.targetAccountId(), triggerId));
                actionId = proposal.actionId();
                actionState = proposal.state();
                actionCriteria = proposal.cappingCriteria();
                runActions.add(new RunAction(actionId, triggerId, planned.type().name(), proposal.state().name(),
                        proposal.level() == null ? null : proposal.level().name(), proposal.cappingCriteria()));
            }
            UUID caseId = null;
            if (trigger.escalation() != null) {
                caseId = escalation.openForTrigger(scope, new EscalationCases.TriggerSnapshot(triggerId,
                        trigger.escalation().summary(), trigger.escalation().reasons()));
            } else if (actionState == ActionState.ESCALATED) {
                // The chip says "With an advisor": an Action the gate could not decide opens the case for it.
                caseId = escalation.openForTrigger(scope, new EscalationCases.TriggerSnapshot(triggerId,
                        NotificationTemplates.escalatedActionCaseSummary(trigger.action().type().name(),
                                trigger.action().amount()), actionCriteria));
            }
            for (DetectedTrigger.PlannedNotification note : trigger.notifications()) {
                UUID id = UUID.randomUUID();
                UUID linked = note.outcome() == NotificationOutcome.ACTION ? actionId : null;
                notifications.insert(scope, new NotificationRepository.Row(id, triggerId, linked, note.outcome(),
                        note.title(), note.body(), note.citedProvIds(), asOf, note.question()));
                audit.write(context, new AuditEntry(AuditKind.NOTIFICATION, null, null, fixtureVersion,
                        body(id, trigger, triggerId, linked, caseId, note)));
                runNotifications.add(new RunNotification(id, NotificationStatus.of(note.outcome(), actionState).name()));
            }
        }
        return new Run(runTriggers, runActions, runNotifications);
    }

    /** Every figure from the Core with origin {@code MONITORING}; no price, holding or index figure is read. */
    private MonitoringSnapshot snapshot(RequestContext context) {
        LiquidityFigures liquidity = core.liquidity(context, ORIGIN);
        ProfileFigures profile = core.profileFigures(context, ORIGIN);
        InsuranceFigures insurance = core.insurance(context, ORIGIN);
        AccountIds accounts = core.accountIds(context);
        RuleChangeFigures rules = core.ruleChanges(context, ORIGIN, RULES.changesAfter(context.asOf()));

        MonitoringSnapshot.Shortfall shortfall = liquidity.shortfallDate() == null ? null
                : new MonitoringSnapshot.Shortfall(liquidity.shortfallDate(),
                        liquidity.shortfall().number(CoreGateway.SHORTFALL), liquidity.lowestDate(),
                        liquidity.shortfall().number(CoreGateway.LOWEST_BALANCE));
        SourcedNumber fund = liquidity.emergencyFund().number(CoreGateway.EMERGENCY_FUND);
        SourcedNumber target = liquidity.emergencyFund().number(CoreGateway.EMERGENCY_FUND_TARGET);
        boolean topUpNeeded = fund.value().compareTo(target.value()) < 0 && shortfall == null
                && accounts.overnight() != null;
        SourcedNumber topUp = topUpNeeded
                ? core.emergencyFundTopUp(context, ORIGIN).number(CoreGateway.EMERGENCY_FUND_TOP_UP)
                : null;

        List<MonitoringSnapshot.PremiumDue> annual = insurance.contracts().stream()
                .filter(c -> c.premium() != null
                        && Integer.valueOf(ANNUAL_MONTHS).equals(c.contract().intervalMonths()))
                .map(c -> new MonitoringSnapshot.PremiumDue(c.contract().id(), c.contract().cover().typeName(),
                        c.nextDueDate(), c.premium().number(CoreGateway.PREMIUM)))
                .toList();
        List<MonitoringSnapshot.Duplicate> duplicates = insurance.duplicateFlags().stream()
                .map(f -> new MonitoringSnapshot.Duplicate(f.cover().name(), f.cover().plainWords(),
                        insurance.duplicates().number(f.outputKey())))
                .toList();
        List<MonitoringSnapshot.RuleChange> changes = rules.changes().stream()
                .map(c -> new MonitoringSnapshot.RuleChange(c.key(), c.version(), c.validFrom(), c.name(),
                        c.figures().number(CoreGateway.RULE_VALUE), previous(c.figures())))
                .toList();
        List<MonitoringSnapshot.Announcement> announcements = rules.announcements().stream()
                .map(a -> new MonitoringSnapshot.Announcement(a.id(), a.topic(), a.title(), a.announcedOn(),
                        a.effectiveFrom(), a.source() != null, a.rulesValid()))
                .toList();
        return new MonitoringSnapshot(accounts.current(), accounts.overnight(), shortfall, fund, target, topUp,
                profile.netIncome().number(CoreGateway.NET_INCOME), annual, duplicates, changes, announcements);
    }

    private static SourcedNumber previous(CoreResult figures) {
        boolean present = figures.outputs().stream()
                .anyMatch(o -> o.outputKey().equals(CoreGateway.RULE_PREVIOUS_VALUE));
        return present ? figures.number(CoreGateway.RULE_PREVIOUS_VALUE) : null;
    }

    private static Map<String, Object> body(UUID notificationId, DetectedTrigger trigger, UUID triggerId,
            UUID actionId, UUID caseId, DetectedTrigger.PlannedNotification note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notificationId", notificationId);
        body.put("triggerId", triggerId);
        body.put("triggerType", trigger.type().name());
        body.put("subjectId", trigger.subjectId());
        body.put("period", trigger.period());
        body.put("outcome", note.outcome().name());
        body.put("actionId", actionId);
        body.put("escalationCaseId", caseId);
        body.put("title", note.title());
        body.put("text", note.body());
        body.put("citedProvIds", note.citedProvIds());
        body.put("question", note.question());
        return body;
    }
}
