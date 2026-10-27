package de.financetool.actions.application;

import de.financetool.actions.ActionProposal;
import de.financetool.actions.Actions;
import de.financetool.actions.ProposedAction;
import de.financetool.actions.adapter.ActionRepository;
import de.financetool.actions.domain.ActionState;
import de.financetool.actions.domain.AutonomyGate;
import de.financetool.actions.domain.AutonomyLevel;
import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.GateContext;
import de.financetool.actions.domain.GateDecision;
import de.financetool.actions.domain.GateThresholds;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.GateMeasures;
import de.financetool.profile.ProfileCompleteness;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
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
 * The only creator of {@code actions} rows and the only builder of the gate context (AD-10): one bank snapshot, the
 * Core's gate measures, suitability completeness and the Mandate lookup.
 */
@Service
class ActionService implements Actions {

    /** The capping criterion of an Action the gate could not decide on. */
    static final String RULE_PARAMETER_MISSING = "rule_parameter_missing";

    /** The topic whose strict suitability fields an investment needs. */
    static final String INVESTMENT_TOPIC = "investment_decision";

    private final BankReadPort bank;
    private final CoreGateway core;
    private final Profiles profiles;
    private final ProfileCompleteness completeness;
    private final MandateLookup mandates;
    private final ActionRepository actions;
    private final AuditWriter audit;

    ActionService(BankReadPort bank, CoreGateway core, Profiles profiles, ProfileCompleteness completeness,
            MandateLookup mandates, ActionRepository actions, AuditWriter audit) {
        this.bank = bank;
        this.core = core;
        this.profiles = profiles;
        this.completeness = completeness;
        this.mandates = mandates;
        this.actions = actions;
        this.audit = audit;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<UUID, ActionState> states(PersonaScope scope, Collection<UUID> actionIds) {
        return actions.states(scope, actionIds);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ActionProposal propose(RequestContext context, PersonaScope scope, String fixtureVersion,
            ProposedAction proposed) {
        if (!scope.personaId().equals(context.personaId())) {
            throw new IllegalArgumentException("the scope is not the context's persona");
        }
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        GateMeasures measures = core.gateMeasures(context, snapshot, proposed.sourceAccountId(), proposed.amount());
        GateAction action = new GateAction(proposed.type(), proposed.regulatoryClass(), proposed.amount(),
                proposed.type().reversible());
        boolean suitabilityComplete = completeness.strictMissing(profiles.stored(scope), INVESTMENT_TOPIC).isEmpty();
        Optional<MandateBounds> mandate = mandates.covering(scope, action);
        GateContext gateContext = new GateContext(measures.liquidAssets(), measures.dataConfidenceHigh(),
                suitabilityComplete, mandate, measures.overnightAfter());

        ActionState state;
        AutonomyLevel level;
        List<String> criteria;
        if (measures.emergencyFundTarget() == null) {
            state = ActionState.ESCALATED;
            level = null;
            criteria = List.of(RULE_PARAMETER_MISSING);
        } else {
            GateDecision decision = AutonomyGate.decide(action, gateContext,
                    new GateThresholds(context.thresholds().investmentShareOfLiquidAssets()));
            state = ActionState.proposedAt(decision.level());
            level = decision.level();
            criteria = decision.cappingCriteria();
        }

        UUID id = UUID.randomUUID();
        actions.insert(scope, new ActionRepository.Row(id, proposed.type(), proposed.regulatoryClass(),
                proposed.amount(), proposed.sourceAccountId(), proposed.targetAccountId(), proposed.triggerId(),
                state, level, criteria, context.asOf()));
        audit.write(context, new AuditEntry(AuditKind.ACTION, null, id, fixtureVersion,
                body(proposed, state, level, criteria, gateContext, measures)));
        return new ActionProposal(id, state, level, criteria);
    }

    private static Map<String, Object> body(ProposedAction proposed, ActionState state, AutonomyLevel level,
            List<String> criteria, GateContext gateContext, GateMeasures measures) {
        Map<String, Object> accounts = new LinkedHashMap<>();
        accounts.put("source", proposed.sourceAccountId());
        accounts.put("target", proposed.targetAccountId());

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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", proposed.type().name());
        body.put("class", proposed.regulatoryClass().id());
        body.put("amount", proposed.amount());
        body.put("accounts", accounts);
        body.put("triggerId", proposed.triggerId());
        body.put("state", state.name());
        body.put("level", level == null ? null : level.name());
        body.put("cappingCriteria", criteria);
        body.put("context", context);
        return body;
    }
}
