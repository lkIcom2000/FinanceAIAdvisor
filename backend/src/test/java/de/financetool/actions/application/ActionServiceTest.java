package de.financetool.actions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.PostgresIntegrationTest;
import de.financetool.actions.ActionConfirmation;
import de.financetool.actions.ActionProposal;
import de.financetool.actions.Actions;
import de.financetool.actions.NotAwaitingConfirmationException;
import de.financetool.actions.ProposedAction;
import de.financetool.actions.adapter.ActionRepository;
import de.financetool.actions.domain.ActionState;
import de.financetool.actions.domain.ActionType;
import de.financetool.actions.domain.AutonomyGate;
import de.financetool.actions.domain.AutonomyLevel;
import de.financetool.actions.domain.GateReasons;
import de.financetool.core.domain.TransferBalances;
import de.financetool.actions.domain.RegulatoryClass;
import de.financetool.audit.AuditWriter;
import de.financetool.bank.BankPort;
import de.financetool.bank.BankReadPort;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.GateMeasures;
import de.financetool.profile.ProfileCompleteness;
import de.financetool.profile.Profiles;
import de.financetool.research.application.ResetPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Story 8.1: Actions are proposed through the Autonomy Gate, stored once and audited in the caller's transaction. */
@ActiveProfiles("research")
class ActionServiceTest extends PostgresIntegrationTest {

    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    static final PersonaId LENA = new PersonaId("p-lena");
    static final String FIXTURE = "test-fixture";

    @Autowired
    Actions actions;

    @Autowired
    RequestContextFactory contexts;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ResetPersonaUseCase resetPersona;

    @Autowired
    BankReadPort bank;

    @Autowired
    BankPort bookings;

    @Autowired
    Profiles profiles;

    @Autowired
    ProfileCompleteness completeness;

    @Autowired
    MandateLookup mandates;

    @Autowired
    ActionRepository repository;

    @Autowired
    AuditWriter audit;

    PersonaScope scope = new PersonaScope(LENA);
    RequestContext context;
    UUID overnight;
    UUID current;

    @BeforeEach
    void lenaAtOctober15() {
        resetPersona.reset(LENA);
        RequestContext base = contexts.forPersona(scope, "run-" + UUID.randomUUID());
        context = new RequestContext(base.agentConfig(), base.configFingerprint(), base.thresholds(),
                base.kbSnapshot(), base.seeds(), OCT_15, base.personaId(), base.runId());
        overnight = account("OVERNIGHT");
        current = account("CURRENT");
    }

    UUID account(String type) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE persona_id = ? AND type = ?", UUID.class,
                LENA.value(), type);
    }

    ActionProposal propose(Actions service, ProposedAction action) {
        return transaction.execute(tx -> service.propose(context, scope, FIXTURE, action));
    }

    List<Map<String, Object>> auditRows(UUID actionId) {
        return jdbc.queryForList("SELECT kind, persona_id, fixture_version, payload::text AS payload "
                + "FROM audit_records WHERE action_id = ?", actionId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lenasShortfallTransferAwaitsHerConfirmationBecauseNoRuleCoversIt() {
        UUID trigger = UUID.randomUUID();
        ActionProposal proposal = propose(actions, new ProposedAction(ActionType.SHORTFALL_TRANSFER,
                RegulatoryClass.PAYMENT, new BigDecimal("300.00"), overnight, current, trigger));

        assertThat(proposal.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);
        assertThat(proposal.level()).isEqualTo(AutonomyLevel.L2);
        assertThat(proposal.cappingCriteria()).containsExactly(AutonomyGate.NO_COVERING_MANDATE);

        List<ActionRepository.Row> rows = repository.findAll(scope);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(proposal.actionId());
            assertThat(row.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);
            assertThat(row.level()).isEqualTo(AutonomyLevel.L2);
            assertThat(row.cappingCriteria()).containsExactly(AutonomyGate.NO_COVERING_MANDATE);
            assertThat(row.amount()).isEqualByComparingTo("300.00");
            assertThat(row.sourceAccountId()).isEqualTo(overnight);
            assertThat(row.targetAccountId()).isEqualTo(current);
            assertThat(row.triggerId()).isEqualTo(trigger);
            assertThat(row.asOf()).isEqualTo(OCT_15);
        });

        List<Map<String, Object>> audits = auditRows(proposal.actionId());
        assertThat(audits).singleElement().satisfies(a -> {
            assertThat(a).containsEntry("kind", "ACTION").containsEntry("persona_id", "p-lena")
                    .containsEntry("fixture_version", FIXTURE);
            Map<?, ?> body = (Map<?, ?>) JsonMapper.builder().build()
                    .readValue((String) a.get("payload"), Map.class).get("body");
            assertThat(body.get("type")).isEqualTo("SHORTFALL_TRANSFER");
            assertThat(body.get("class")).isEqualTo("payment");
            assertThat(body.get("state")).isEqualTo("AWAITING_CONFIRMATION");
            assertThat(body.get("level")).isEqualTo("L2");
            assertThat(body.get("cappingCriteria")).isEqualTo(List.of(AutonomyGate.NO_COVERING_MANDATE));
            assertThat(body.get("triggerId")).isEqualTo(trigger.toString());
            assertThat((Map<String, Object>) body.get("accounts")).containsEntry("source", overnight.toString())
                    .containsEntry("target", current.toString());
            assertThat((Map<String, Object>) body.get("context")).containsEntry("dataConfidenceHigh", true)
                    .containsKeys("liquidAssets", "suitabilityComplete", "emergencyFundAfter", "emergencyFundTarget");
        });
    }

    @Test
    void insuranceAndLegalActionsAreOnlyRecommended() {
        for (RegulatoryClass regulatoryClass : List.of(RegulatoryClass.INSURANCE, RegulatoryClass.LEGAL)) {
            ActionProposal proposal = propose(actions, new ProposedAction(ActionType.SHORTFALL_TRANSFER,
                    regulatoryClass, new BigDecimal("50.00"), overnight, current, null));

            assertThat(proposal.state()).as(regulatoryClass.id()).isEqualTo(ActionState.RECOMMENDED);
            assertThat(proposal.level()).isEqualTo(AutonomyLevel.L1);
        }
        assertThat(repository.findAll(scope)).extracting(ActionRepository.Row::state)
                .containsOnly(ActionState.RECOMMENDED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM actions WHERE persona_id = ? AND state = 'EXECUTED'",
                Long.class, LENA.value())).isZero();
    }

    @Test
    void aNegativeAmountIsRejected() {
        assertThatThrownBy(() -> new ProposedAction(ActionType.SHORTFALL_TRANSFER, RegulatoryClass.PAYMENT,
                new BigDecimal("-0.01"), overnight, current, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anotherPersonasScopeIsRejected() {
        ProposedAction action = new ProposedAction(ActionType.SHORTFALL_TRANSFER, RegulatoryClass.PAYMENT,
                new BigDecimal("300.00"), overnight, current, null);
        PersonaScope marco = new PersonaScope(new PersonaId("p-marco"));

        assertThatThrownBy(() -> transaction.execute(tx -> actions.propose(context, marco, FIXTURE, action)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findAll(scope)).isEmpty();
    }

    @Test
    void proposeRunsOnlyInsideTheCallersTransaction() {
        ProposedAction action = new ProposedAction(ActionType.SHORTFALL_TRANSFER, RegulatoryClass.PAYMENT,
                new BigDecimal("300.00"), overnight, current, null);

        assertThatThrownBy(() -> actions.propose(context, scope, FIXTURE, action))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aPersonaResetDeletesThePersonasActions() {
        propose(actions, new ProposedAction(ActionType.EF_TOP_UP, RegulatoryClass.PAYMENT, new BigDecimal("100.00"),
                current, overnight, null));
        assertThat(repository.findAll(scope)).hasSize(1);

        resetPersona.reset(LENA);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM actions WHERE persona_id = ?", Long.class,
                LENA.value())).isZero();
    }

    @Test
    void withoutAValidRuleParameterTheActionIsEscalatedAndStillAudited() {
        CoreGateway missingRule = mock(CoreGateway.class);
        when(missingRule.gateMeasures(any(), any(), any(), any())).thenReturn(new GateMeasures(
                new BigDecimal("5000.00"), true, BigDecimal.ONE, 1, new BigDecimal("2700.00"), null));
        ActionService service = new ActionService(bank, bookings, missingRule, profiles, completeness, mandates, repository,
                audit);

        ActionProposal proposal = propose(service, new ProposedAction(ActionType.SHORTFALL_TRANSFER,
                RegulatoryClass.PAYMENT, new BigDecimal("300.00"), overnight, current, null));

        assertThat(proposal.state()).isEqualTo(ActionState.ESCALATED);
        assertThat(proposal.level()).isNull();
        assertThat(proposal.cappingCriteria()).containsExactly(ActionService.RULE_PARAMETER_MISSING);
        assertThat(repository.findAll(scope)).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo(ActionState.ESCALATED);
            assertThat(row.level()).isNull();
        });
        assertThat(auditRows(proposal.actionId())).singleElement()
                .satisfies(a -> assertThat(a).containsEntry("kind", "ACTION"));
    }

    ActionService serviceWith(boolean dataConfidenceHigh, BigDecimal emergencyFundTarget) {
        CoreGateway core = mock(CoreGateway.class);
        when(core.gateMeasures(any(), any(), any(), any())).thenReturn(new GateMeasures(new BigDecimal("5000.00"),
                dataConfidenceHigh, BigDecimal.ONE, 1, new BigDecimal("2700.00"), emergencyFundTarget));
        when(core.transferBalances(any(), any(), any(), any(), any())).thenReturn(new TransferBalances(
                new BigDecimal("1850.00"), new BigDecimal("1750.00"), new BigDecimal("2700.00"),
                new BigDecimal("2800.00")));
        return new ActionService(bank, bookings, core, profiles, completeness, mandates, repository, audit);
    }

    @Test
    void aReCheckThatNoLongerAllowsTheTransferKeepsItWaitingWithTheNewReason() {
        ActionProposal proposal = propose(actions, new ProposedAction(ActionType.SHORTFALL_TRANSFER,
                RegulatoryClass.PAYMENT, new BigDecimal("300.00"), overnight, current, null));
        assertThat(proposal.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);

        var lowData = transaction.execute(tx -> serviceWith(false, new BigDecimal("6000.00"))
                .confirm(context, scope, FIXTURE, proposal.actionId())).orElseThrow();
        assertThat(lowData.done()).isFalse();
        assertThat(lowData.reason())
                .isEqualTo("I can only suggest this, because some of your recent account data is missing.");
        assertThat(repository.findAll(scope)).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);
            assertThat(row.cappingCriteria())
                    .containsExactly(AutonomyGate.NO_COVERING_MANDATE, AutonomyGate.DATA_CONFIDENCE_LOW);
            assertThat(row.reason()).isEqualTo(lowData.reason());
        });

        var missingRule = transaction.execute(tx -> serviceWith(true, null)
                .confirm(context, scope, FIXTURE, proposal.actionId())).orElseThrow();
        assertThat(missingRule.done()).isFalse();
        assertThat(missingRule.reason()).startsWith("I can't make this transfer right now");
        assertThat(repository.findAll(scope)).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);
            assertThat(row.cappingCriteria()).containsExactly(ActionService.RULE_PARAMETER_MISSING);
        });
        assertThat(jdbc.queryForList("SELECT payload->'body'->>'outcome' FROM audit_records WHERE action_id = ?"
                + " AND kind = 'ACTION' ORDER BY seq", String.class, proposal.actionId()))
                .containsExactly(null, "refused", "refused");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE persona_id = ?"
                + " AND origin = 'BANK_PORT'", Long.class, LENA.value())).isZero();
    }

    RequestContext at(LocalDate asOf) {
        return new RequestContext(context.agentConfig(), context.configFingerprint(), context.thresholds(),
                context.kbSnapshot(), context.seeds(), asOf, context.personaId(), context.runId());
    }

    @Test
    void onlyTheNewestTransferOfAKindCanBeConfirmedSoMoneyNeverMovesTwice() {
        ProposedAction transfer = new ProposedAction(ActionType.SHORTFALL_TRANSFER, RegulatoryClass.PAYMENT,
                new BigDecimal("100.00"), overnight, current, null);
        ActionProposal older = propose(actions, transfer);
        ActionProposal newer = transaction.execute(tx -> actions.propose(at(OCT_15.plusDays(1)), scope, FIXTURE,
                transfer));

        // At the older date the newer one does not exist yet: the older stays confirmable there.
        boolean newerAtOct15 = repository.hasNewerAwaiting(scope,
                repository.find(scope, older.actionId()).orElseThrow(), OCT_15);
        assertThat(newerAtOct15).isFalse();
        var replaced = transaction.execute(tx -> actions.confirm(at(OCT_15.plusDays(1)), scope, FIXTURE,
                older.actionId())).orElseThrow();
        assertThat(replaced.done()).isFalse();
        assertThat(replaced.reason())
                .isEqualTo("I can't make this transfer because a newer suggestion of the same kind replaces it.");
        assertThat(transaction.execute(tx -> actions.confirm(at(OCT_15.plusDays(1)), scope, FIXTURE,
                newer.actionId())).orElseThrow().done()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE persona_id = ?"
                + " AND origin = 'BANK_PORT'", Long.class, LENA.value())).isEqualTo(2);
    }

    @Test
    void aTopUpBeyondTheEmergencyFundTargetIsRefused() {
        ActionProposal topUp = propose(actions, new ProposedAction(ActionType.EF_TOP_UP, RegulatoryClass.PAYMENT,
                new BigDecimal("100.00"), current, overnight, null));
        assertThat(topUp.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);

        ActionService service = serviceWith(true, new BigDecimal("1.00"));
        var reached = transaction.execute(tx -> service.confirm(context, scope, FIXTURE, topUp.actionId()))
                .orElseThrow();

        assertThat(reached.done()).isFalse();
        assertThat(reached.reason()).isEqualTo(
                "I can't make this transfer because your emergency fund has already reached its target of 1.00 EUR.");
        assertThat(repository.findAll(scope)).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);
            assertThat(row.cappingCriteria()).containsExactly(GateReasons.NO_LONGER_NEEDED);
            assertThat(row.level()).isEqualTo(AutonomyLevel.L2);
        });
    }

    @Test
    void anActionProposedAfterTheSimulatedDateCannotBeReviewedOrConfirmed() {
        ActionProposal later = transaction.execute(tx -> actions.propose(at(OCT_15.plusDays(10)), scope, FIXTURE,
                new ProposedAction(ActionType.SHORTFALL_TRANSFER, RegulatoryClass.PAYMENT, new BigDecimal("100.00"),
                        overnight, current, null)));

        assertThatThrownBy(() -> transaction.execute(tx -> actions.confirm(context, scope, FIXTURE,
                later.actionId()))).isInstanceOf(NotAwaitingConfirmationException.class);
        assertThatThrownBy(() -> transaction.execute(tx -> actions.review(context, scope, later.actionId())))
                .isInstanceOf(NotAwaitingConfirmationException.class);
    }

    @Test
    void onlyATransferAwaitingConfirmationCanBeReviewedOrConfirmed() {
        ActionProposal recommended = transaction.execute(tx -> serviceWith(false, new BigDecimal("6000.00"))
                .propose(context, scope, FIXTURE, new ProposedAction(ActionType.SHORTFALL_TRANSFER,
                        RegulatoryClass.PAYMENT, new BigDecimal("300.00"), overnight, current, null)));
        assertThat(recommended.state()).isEqualTo(ActionState.RECOMMENDED);
        ActionProposal selfTransfer = propose(actions, new ProposedAction(ActionType.SHORTFALL_TRANSFER,
                RegulatoryClass.PAYMENT, new BigDecimal("300.00"), overnight, overnight, null));
        assertThat(selfTransfer.state()).isEqualTo(ActionState.AWAITING_CONFIRMATION);

        for (UUID id : List.of(recommended.actionId(), selfTransfer.actionId())) {
            assertThatThrownBy(() -> transaction.execute(tx -> actions.confirm(context, scope, FIXTURE, id)))
                    .isInstanceOf(NotAwaitingConfirmationException.class);
            assertThatThrownBy(() -> transaction.execute(tx -> actions.review(context, scope, id)))
                    .isInstanceOf(NotAwaitingConfirmationException.class);
        }
        Optional<ActionConfirmation> unknown = transaction.execute(tx -> actions.confirm(context, scope, FIXTURE,
                UUID.randomUUID()));
        assertThat(unknown).isEmpty();
    }
}
