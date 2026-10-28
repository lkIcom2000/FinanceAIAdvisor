package de.financetool.actions.application;

import static de.financetool.actions.application.ActionService.account;
import static de.financetool.actions.application.ActionService.requireSamePersona;

import de.financetool.actions.InvalidRuleException;
import de.financetool.actions.Rule;
import de.financetool.actions.RuleAlreadyActiveException;
import de.financetool.actions.RuleAlreadyStoppedException;
import de.financetool.actions.RuleCheck;
import de.financetool.actions.RuleSetup;
import de.financetool.actions.Rules;
import de.financetool.actions.adapter.MandateRepository;
import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.actions.domain.RuleTexts;
import de.financetool.actions.domain.UndoWindow;
import de.financetool.audit.AuditEntry;
import de.financetool.audit.AuditKind;
import de.financetool.audit.AuditWriter;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.bank.AccountType;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.RuleLimits;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only writer of {@code mandates} rows (AD-10): starts, lists and stops rules; the gate reads them through
 * {@link MandateLookup}.
 */
@Service
class RuleService implements Rules {

    private final BankReadPort bank;
    private final CoreGateway core;
    private final MandateLookup mandates;
    private final MandateRepository mandateRows;
    private final AuditWriter audit;

    RuleService(BankReadPort bank, CoreGateway core, MandateLookup mandates, MandateRepository mandateRows,
            AuditWriter audit) {
        this.bank = bank;
        this.core = core;
        this.mandates = mandates;
        this.mandateRows = mandateRows;
        this.audit = audit;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public int activeRules(PersonaScope scope) {
        return mandates.active(scope);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Rule> rules(RequestContext context, PersonaScope scope) {
        requireSamePersona(context, scope);
        List<MandateRepository.Row> rows = mandateRows.all(scope);
        if (rows.isEmpty()) {
            return List.of();
        }
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        Map<UUID, Integer> counts = mandateRows.actionCounts(scope);
        Map<UUID, LocalDate> lastExecuted = mandateRows.lastExecuted(scope, context.asOf());
        return rows.stream().map(r -> rule(r, snapshot, counts, lastExecuted, context.asOf())).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Rule> revokeRule(RequestContext context, PersonaScope scope, String fixtureVersion,
            UUID ruleId) {
        requireSamePersona(context, scope);
        Optional<MandateRepository.Row> found = mandateRows.findForUpdate(scope, ruleId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        // A simulated date before the rule's start (persona date moved back) still stops it, on its start date.
        LocalDate revokedAt = context.asOf().isBefore(found.get().createdAt()) ? found.get().createdAt()
                : context.asOf();
        if (found.get().revokedAt() != null || !mandateRows.revoke(scope, ruleId, revokedAt)) {
            throw new RuleAlreadyStoppedException();
        }
        MandateRepository.Row row = found.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "rule_revoked");
        body.put("mandateId", row.id());
        body.put("template", row.template().name());
        body.put("revokedAt", revokedAt);
        audit.write(context, new AuditEntry(AuditKind.ACTION, null, row.id(), fixtureVersion, body));

        MandateRepository.Row revoked = new MandateRepository.Row(row.id(), row.template(), row.bounds(),
                row.sourceAccountId(), row.targetAccountId(), row.createdAt(), revokedAt);
        return Optional.of(rule(revoked, bank.snapshot(scope.personaId(), context.asOf()),
                mandateRows.actionCounts(scope), mandateRows.lastExecuted(scope, context.asOf()), context.asOf()));
    }

    private static Rule rule(MandateRepository.Row r, BankSnapshot snapshot, Map<UUID, Integer> counts,
            Map<UUID, LocalDate> lastExecuted, LocalDate asOf) {
        LocalDate undoUntil = Optional.ofNullable(lastExecuted.get(r.id()))
                .filter(executedOn -> UndoWindow.isOpen(executedOn, asOf))
                .map(UndoWindow::deadline)
                .orElse(null);
        return new Rule(r.id(), r.template(), RuleTexts.summary(r.template(), r.bounds()),
                account(snapshot, r.sourceAccountId()).map(BankSnapshot.Account::name).orElse(null),
                account(snapshot, r.targetAccountId()).map(BankSnapshot.Account::name).orElse(null),
                r.bounds(), r.createdAt(), counts.getOrDefault(r.id(), 0), r.revokedAt(), undoUntil);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RuleSetup> ruleSetup(RequestContext context, PersonaScope scope) {
        requireSamePersona(context, scope);
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        Optional<BankSnapshot.Account> current = accountOfType(snapshot, AccountType.CURRENT);
        Optional<BankSnapshot.Account> overnight = accountOfType(snapshot, AccountType.OVERNIGHT);
        if (current.isEmpty() || overnight.isEmpty()) {
            return List.of();
        }
        CoreResult limits = core.ruleLimits(context, EvidenceOrigin.OVERVIEW);
        return List.of(
                setup(scope, RuleTemplate.SAFETY_CUSHION, overnight.get(), current.get(),
                        limits.number(RuleLimits.OUTPUT_CUSHION_MAX_PER_TRANSFER),
                        limits.number(RuleLimits.OUTPUT_CUSHION_MAX_PER_MONTH),
                        limits.number(RuleLimits.OUTPUT_CUSHION_FLOOR)),
                setup(scope, RuleTemplate.EMERGENCY_FUND_TOP_UP, current.get(), overnight.get(),
                        limits.number(RuleLimits.OUTPUT_TOP_UP_MAX_PER_TRANSFER),
                        limits.number(RuleLimits.OUTPUT_TOP_UP_MAX_PER_MONTH),
                        limits.number(RuleLimits.OUTPUT_TOP_UP_FLOOR)));
    }

    @Override
    public RuleCheck checkRule(RuleTemplate template, RuleBounds bounds) {
        requireValid(bounds);
        return new RuleCheck(RuleTexts.summary(template, bounds), RuleTexts.will(template, bounds),
                RuleTexts.wont(template, bounds), RuleTexts.UNDO_AND_STOP);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Rule createRule(RequestContext context, PersonaScope scope, String fixtureVersion, RuleTemplate template,
            RuleBounds bounds) {
        requireSamePersona(context, scope);
        requireValid(bounds);
        BankSnapshot snapshot = bank.snapshot(scope.personaId(), context.asOf());
        Optional<BankSnapshot.Account> current = accountOfType(snapshot, AccountType.CURRENT);
        Optional<BankSnapshot.Account> overnight = accountOfType(snapshot, AccountType.OVERNIGHT);
        if (current.isEmpty() || overnight.isEmpty()) {
            throw new InvalidRuleException("A rule needs both a current account and an overnight deposit.");
        }
        BankSnapshot.Account from = template.fromOvernight() ? overnight.get() : current.get();
        BankSnapshot.Account to = template.fromOvernight() ? current.get() : overnight.get();
        if (mandateRows.hasActive(scope, template)) {
            throw new RuleAlreadyActiveException();
        }
        MandateRepository.Row row = new MandateRepository.Row(UUID.randomUUID(), template, bounds, from.id(), to.id(),
                context.asOf(), null);
        try {
            mandateRows.insert(scope, row);
        } catch (DuplicateKeyException e) {
            throw new RuleAlreadyActiveException();
        }
        audit.write(context, new AuditEntry(AuditKind.ACTION, null, row.id(), fixtureVersion, ruleBody(row)));
        return new Rule(row.id(), template, RuleTexts.summary(template, bounds), from.name(), to.name(), bounds,
                row.createdAt(), 0, null, null);
    }

    private RuleSetup setup(PersonaScope scope, RuleTemplate template, BankSnapshot.Account from,
            BankSnapshot.Account to, SourcedNumber maxPerAction, SourcedNumber maxPerMonth, SourcedNumber floor) {
        return new RuleSetup(template, template.title(), from.name(), to.name(), mandateRows.hasActive(scope, template),
                maxPerAction, maxPerMonth, floor, RuleTexts.source(template));
    }

    private static void requireValid(RuleBounds bounds) {
        RuleTexts.problem(bounds).ifPresent(problem -> {
            throw new InvalidRuleException(problem);
        });
    }

    private static Optional<BankSnapshot.Account> accountOfType(BankSnapshot snapshot, AccountType type) {
        return snapshot.accounts().stream().filter(a -> a.type() == type).findFirst();
    }

    /** The Audit Record of a started rule; its id stands in the record's Action id. */
    private static Map<String, Object> ruleBody(MandateRepository.Row row) {
        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put("maxPerAction", row.bounds().maxPerAction());
        bounds.put("maxPerMonth", row.bounds().maxPerMonth());
        bounds.put("floor", row.bounds().floor());
        Map<String, Object> accounts = new LinkedHashMap<>();
        accounts.put("source", row.sourceAccountId());
        accounts.put("target", row.targetAccountId());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", "rule_created");
        body.put("mandateId", row.id());
        body.put("template", row.template().name());
        body.put("actionType", row.template().actionType().name());
        body.put("bounds", bounds);
        body.put("accounts", accounts);
        body.put("createdAt", row.createdAt());
        return body;
    }
}
