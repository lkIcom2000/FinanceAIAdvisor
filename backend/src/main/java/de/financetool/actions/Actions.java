package de.financetool.actions;

import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Module API: the only creator of Actions (AD-10). Chat never calls it (AD-3). */
public interface Actions {

    /**
     * Runs the Autonomy Gate on the Action against one bank snapshot at {@code context.asOf()}, stores the Action in
     * the state its level allows (L0 informed, L1 recommended, L2 awaiting confirmation) and writes an {@code ACTION}
     * Audit Record, in the caller's transaction. Nothing is executed. Without a valid Rule Parameter for the
     * emergency-fund target the Action is stored {@code ESCALATED} without a level.
     *
     * @param fixtureVersion the persona's fixture version, for the Audit Record
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    ActionProposal propose(RequestContext context, PersonaScope scope, String fixtureVersion, ProposedAction action);

    /**
     * The current states and reasons of the scope's Actions with these ids, in the caller's transaction; an id that is
     * not one of the persona's Actions is left out.
     */
    Map<UUID, ActionSummary> summaries(PersonaScope scope, Collection<UUID> actionIds);

    /** The number of the scope's rules (Mandates) that are not revoked, in the caller's transaction. */
    int activeRules(PersonaScope scope);

    /**
     * The scope's rules that are not revoked, oldest first, read only in the caller's transaction.
     *
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    List<Rule> rules(RequestContext context, PersonaScope scope);

    /**
     * The rule templates with the limits the Core suggests at {@code context.asOf()} (its figures are stored as
     * evidence in the caller's transaction); empty if the persona lacks a current account or an overnight deposit.
     *
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    List<RuleSetup> ruleSetup(RequestContext context, PersonaScope scope);

    /**
     * The "Check and confirm" texts of a rule; nothing is stored.
     *
     * @throws InvalidRuleException if the limits can't be used
     */
    RuleCheck checkRule(RuleTemplate template, RuleBounds bounds);

    /**
     * Starts a rule (FR-36, AD-10) on {@code context.asOf()} with the template's accounts and writes an {@code ACTION}
     * Audit Record ({@code event: rule_created}), in the caller's transaction.
     *
     * @param fixtureVersion the persona's fixture version, for the Audit Record
     * @throws InvalidRuleException if the limits can't be used or the persona lacks one of the accounts
     * @throws RuleAlreadyActiveException if a rule of the template is already active
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    Rule createRule(RequestContext context, PersonaScope scope, String fixtureVersion, RuleTemplate template,
            RuleBounds bounds);

    /**
     * The L2 dialog for the scope's Action at {@code context.asOf()}: balances from the Core, the stored reason. Read
     * only, in the caller's transaction.
     *
     * @return empty if the Action is not the persona's
     * @throws NotAwaitingConfirmationException if it is not a transfer awaiting confirmation, or an account is gone
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    Optional<ActionReview> review(RequestContext context, PersonaScope scope, UUID actionId);

    /**
     * Confirms the scope's Action (FR-40, AD-10): re-runs the gate and the checks on the same row at
     * {@code context.asOf()}. On success the transfer is booked through the bank and the row becomes {@code EXECUTED};
     * otherwise the row stays {@code AWAITING_CONFIRMATION} with the new criteria and reason. Either way an
     * {@code ACTION} Audit Record is written in the caller's transaction; no new row is ever created.
     *
     * @param fixtureVersion the persona's fixture version, for the Audit Record
     * @return empty if the Action is not the persona's
     * @throws NotAwaitingConfirmationException if it is not (or no longer) a transfer awaiting confirmation
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    Optional<ActionConfirmation> confirm(RequestContext context, PersonaScope scope, String fixtureVersion,
            UUID actionId);

    /**
     * Undoes a transfer Fine made within a rule (FR-38, AD-10) at {@code context.asOf()}, without the gate: books the
     * reverse transfer through the bank, stores a compensating Action linked by {@code undoesId} and sets the original
     * to {@code UNDONE}, with an {@code ACTION} Audit Record, in the caller's transaction.
     *
     * @param fixtureVersion the persona's fixture version, for the Audit Record
     * @return empty if the Action is not the persona's
     * @throws NotUndoableException if it is not a rule transfer, already undone, or its window has ended
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    Optional<ActionUndo> undo(RequestContext context, PersonaScope scope, String fixtureVersion, UUID actionId);
}
