package de.financetool.actions.application;

import de.financetool.actions.ActionConfirmation;
import de.financetool.actions.ActionProposal;
import de.financetool.actions.ActionReview;
import de.financetool.actions.ActionSummary;
import de.financetool.actions.Actions;
import de.financetool.actions.NotAwaitingConfirmationException;
import de.financetool.actions.ProposedAction;
import de.financetool.actions.adapter.ActionRepository;
import de.financetool.actions.domain.ActionState;
import de.financetool.actions.domain.ActionType;
import de.financetool.actions.domain.AutonomyGate;
import de.financetool.actions.domain.AutonomyLevel;
import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.GateContext;
import de.financetool.actions.domain.GateDecision;
import de.financetool.actions.domain.GateReasons;
import de.financetool.actions.domain.GateThresholds;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.actions.domain.RegulatoryClass;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.bank.BankPort;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.GateMeasures;
import de.financetool.core.domain.TransferBalances;
import de.financetool.profile.ProfileCompleteness;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@code actions} rows and the only builder of the gate context (AD-10): one bank snapshot, the
 * Core's gate measures, suitability completeness and the Mandate lookup. Execution goes only through {@link BankPort}.
 */
@Service
class ActionService implements Actions {

    /** The capping criterion of an Action the gate could not decide on. */
    static final String RULE_PARAMETER_MISSING = GateReasons.RULE_PARAMETER_MISSING;

    /** The topic whose strict suitability fields an investment needs. */
    static final String INVESTMENT_TOPIC = "investment_decision";

    /** The booking reference of a confirmed transfer. */
    static final String CONFIRMED_REFERENCE = "Transfer you confirmed in Fine";

    private final BankReadPort bank;
    private final BankPort bookings;
    private final CoreGateway core;
    private final Profiles profiles;
    private final ProfileCompleteness completeness;
    private final MandateLookup mandates;
    private final ActionRepository actions;
    private final AuditWriter audit;

    ActionService(BankReadPort bank, BankPort bookings, CoreGateway core, Profiles profiles,
            ProfileCompleteness completeness, MandateLookup mandates, ActionRepository actions, AuditWriter audit) {
        this.bank = bank;
        this.bookings = bookings;
        this.core = core;
        this.profiles = profiles;
        this.completeness = completeness;
        this.mandates = mandates;
        this.actions = actions;
        this.audit = audit;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<UUID, ActionSummary> summaries(PersonaScope scope, Collection<UUID> actionIds) {
        return actions.summaries(scope, actionIds);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public int activeRules(PersonaScope scope) {
        return mandates.active(scope);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionProposal propose(RequestContext context, PersonaScope scope, String fixtureVersion,
            ProposedAction proposed) {
        requireSamePersona(context, scope);
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        Gate gate = gate(context, scope, snapshot, proposed.type(), proposed.regulatoryClass(), proposed.amount(),
                proposed.sourceAccountId());

        ActionState state;
        AutonomyLevel level;
        if (gate.decision() == null) {
            state = ActionState.ESCALATED;
            level = null;
        } else {
            state = ActionState.proposedAt(gate.decision().level());
            level = gate.decision().level();
        }
        List<String> criteria = gate.criteria();

        UUID id = UUID.randomUUID();
        actions.insert(scope, new ActionRepository.Row(id, proposed.type(), proposed.regulatoryClass(),
                proposed.amount(), proposed.sourceAccountId(), proposed.targetAccountId(), proposed.triggerId(),
                state, level, criteria, context.asOf(), gate.reason(proposed.amount())));
        audit.write(context, new AuditEntry(AuditKind.ACTION, null, id, fixtureVersion,
                body(proposed, state, level, criteria, gate)));
        return new ActionProposal(id, state, level, criteria);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ActionReview> review(RequestContext context, PersonaScope scope, UUID actionId) {
        requireSamePersona(context, scope);
        Optional<ActionRepository.Row> found = actions.find(scope, actionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ActionRepository.Row row = requireAwaitingTransfer(found.get());
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        Optional<BankSnapshot.Account> from = account(snapshot, row.sourceAccountId());
        Optional<BankSnapshot.Account> to = account(snapshot, row.targetAccountId());
        if (from.isEmpty() || to.isEmpty()) {
            throw new NotAwaitingConfirmationException();
        }
        TransferBalances balances = core.transferBalances(context, snapshot, row.sourceAccountId(),
                row.targetAccountId(), row.amount());
        return Optional.of(new ActionReview(row.amount(), from.get().name(), balances.fromBefore(),
                balances.fromAfter(), to.get().name(), balances.toBefore(), balances.toAfter(), row.reason(),
                GateReasons.why(row.cappingCriteria())));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ActionConfirmation> confirm(RequestContext context, PersonaScope scope, String fixtureVersion,
            UUID actionId) {
        requireSamePersona(context, scope);
        Optional<ActionRepository.Row> found = actions.findForUpdate(scope, actionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ActionRepository.Row row = requireAwaitingTransfer(found.get());
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        Optional<BankSnapshot.Account> from = account(snapshot, row.sourceAccountId());
        Optional<BankSnapshot.Account> to = account(snapshot, row.targetAccountId());

        Gate gate = null;
        List<String> refusal = null;
        String reason = null;
        if (from.isEmpty() || to.isEmpty()) {
            refusal = List.of(GateReasons.ACCOUNT_MISSING);
        } else {
            gate = gate(context, scope, snapshot, row.type(), row.regulatoryClass(), row.amount(),
                    row.sourceAccountId());
            if (gate.decision() == null || gate.decision().level().compareTo(AutonomyLevel.L2) < 0) {
                refusal = gate.criteria();
            } else {
                BigDecimal fromBefore = core.transferBalances(context, snapshot, row.sourceAccountId(),
                        row.targetAccountId(), row.amount()).fromBefore();
                if (fromBefore.compareTo(row.amount()) < 0) {
                    refusal = List.of(GateReasons.SOURCE_NOT_COVERING);
                    reason = GateReasons.notCovering(from.get().name(), fromBefore);
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "confirm");
        ActionConfirmation confirmation;
        if (refusal != null) {
            if (reason == null) {
                reason = GateReasons.reason(refusal, row.amount(), gate == null ? Optional.empty() : gate.mandate());
            }
            actions.updateAfterCheck(scope, row.id(), ActionState.AWAITING_CONFIRMATION, row.level(), refusal, reason);
            body.put("outcome", "refused");
            body.put("state", ActionState.AWAITING_CONFIRMATION.name());
            body.put("level", row.level() == null ? null : row.level().name());
            body.put("cappingCriteria", refusal);
            body.put("transfer", null);
            confirmation = new ActionConfirmation(false, row.amount(), reason, GateReasons.why(refusal));
        } else {
            BankPort.Transfer transfer = bookings.transfer(scope.personaId(), row.sourceAccountId(),
                    row.targetAccountId(), row.amount(), context.asOf(), CONFIRMED_REFERENCE);
            GateDecision decision = gate.decision();
            actions.updateAfterCheck(scope, row.id(), ActionState.EXECUTED, decision.level(),
                    decision.cappingCriteria(), gate.reason(row.amount()));
            Map<String, Object> booked = new LinkedHashMap<>();
            booked.put("debitTransactionId", transfer.debitTransactionId());
            booked.put("creditTransactionId", transfer.creditTransactionId());
            body.put("outcome", "executed");
            body.put("state", ActionState.EXECUTED.name());
            body.put("level", decision.level().name());
            body.put("cappingCriteria", decision.cappingCriteria());
            body.put("transfer", booked);
            confirmation = new ActionConfirmation(true, row.amount(), null, null);
        }
        body.put("amount", row.amount());
        body.put("context", gate == null ? null : gateContextBody(gate));
        audit.write(context, new AuditEntry(AuditKind.ACTION, null, row.id(), fixtureVersion, body));
        return Optional.of(confirmation);
    }

    /** The gate's inputs and decision; {@code decision} is {@code null} without a valid emergency-fund parameter. */
    private record Gate(GateContext context, GateMeasures measures, GateDecision decision) {

        List<String> criteria() {
            return decision == null ? List.of(RULE_PARAMETER_MISSING) : decision.cappingCriteria();
        }

        Optional<MandateBounds> mandate() {
            return context.mandate();
        }

        String reason(BigDecimal amount) {
            return GateReasons.reason(criteria(), amount, mandate());
        }
    }

    private Gate gate(RequestContext context, PersonaScope scope, BankSnapshot snapshot, ActionType type,
            RegulatoryClass regulatoryClass, BigDecimal amount, UUID sourceAccountId) {
        GateMeasures measures = core.gateMeasures(context, snapshot, sourceAccountId, amount);
        GateAction action = new GateAction(type, regulatoryClass, amount, type.reversible());
        boolean suitabilityComplete = completeness.strictMissing(profiles.stored(scope), INVESTMENT_TOPIC).isEmpty();
        Optional<MandateBounds> mandate = mandates.covering(scope, action);
        GateContext gateContext = new GateContext(measures.liquidAssets(), measures.dataConfidenceHigh(),
                suitabilityComplete, mandate, measures.overnightAfter());
        GateDecision decision = measures.emergencyFundTarget() == null ? null
                : AutonomyGate.decide(action, gateContext,
                        new GateThresholds(context.thresholds().investmentShareOfLiquidAssets()));
        return new Gate(gateContext, measures, decision);
    }

    private static ActionRepository.Row requireAwaitingTransfer(ActionRepository.Row row) {
        if (row.state() != ActionState.AWAITING_CONFIRMATION || row.sourceAccountId() == null
                || row.targetAccountId() == null || row.sourceAccountId().equals(row.targetAccountId())
                || row.amount() == null || row.amount().signum() <= 0
                || row.regulatoryClass() == RegulatoryClass.INSURANCE
                || row.regulatoryClass() == RegulatoryClass.LEGAL) {
            throw new NotAwaitingConfirmationException();
        }
        return row;
    }

    private static Optional<BankSnapshot.Account> account(BankSnapshot snapshot, UUID accountId) {
        return snapshot.accounts().stream().filter(a -> a.id().equals(accountId)).findFirst();
    }

    private static void requireSamePersona(RequestContext context, PersonaScope scope) {
        if (!scope.personaId().equals(context.personaId())) {
            throw new IllegalArgumentException("the scope is not the context's persona");
        }
    }

    private static Map<String, Object> body(ProposedAction proposed, ActionState state, AutonomyLevel level,
            List<String> criteria, Gate gate) {
        Map<String, Object> accounts = new LinkedHashMap<>();
        accounts.put("source", proposed.sourceAccountId());
        accounts.put("target", proposed.targetAccountId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", proposed.type().name());
        body.put("class", proposed.regulatoryClass().id());
        body.put("amount", proposed.amount());
        body.put("accounts", accounts);
        body.put("triggerId", proposed.triggerId());
        body.put("state", state.name());
        body.put("level", level == null ? null : level.name());
        body.put("cappingCriteria", criteria);
        body.put("context", gateContextBody(gate));
        return body;
    }

    private static Map<String, Object> gateContextBody(Gate gate) {
        GateContext gateContext = gate.context();
        GateMeasures measures = gate.measures();
        Map<String, Object> mandate = gateContext.mandate()
                .map(m -> {
                    Map<String, Object> bounds = new LinkedHashMap<>();
                    bounds.put("maxPerAction", m.maxPerAction());
                    bounds.put("floor", m.floor());
                    return bounds;
                })
                .orElse(null);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("liquidAssets", gateContext.liquidAssets());
        context.put("dataConfidenceHigh", gateContext.dataConfidenceHigh());
        context.put("dataConfidenceCoverage", measures.dataConfidenceCoverage());
        context.put("dataConfidenceLongestGapDays", measures.dataConfidenceLongestGapDays());
        context.put("suitabilityComplete", gateContext.suitabilityComplete());
        context.put("mandate", mandate);
        context.put("emergencyFundAfter", gateContext.emergencyFundAfter());
        context.put("emergencyFundTarget", measures.emergencyFundTarget());
        return context;
    }
}
