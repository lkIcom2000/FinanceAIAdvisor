package de.financetool.actions;

import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Module API: the only writer of rules (Mandates) the Autonomy Gate checks Actions against (AD-10). */
public interface Rules {

    /** The number of the scope's rules (Mandates) that are not revoked, in the caller's transaction. */
    int activeRules(PersonaScope scope);

    /**
     * The scope's rules: those not revoked, oldest first, then the revoked ones, latest revocation first; read only in
     * the caller's transaction.
     *
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    List<Rule> rules(RequestContext context, PersonaScope scope);

    /**
     * Stops a rule (FR-38, AD-10) on {@code context.asOf()}: sets its revocation date, never deletes it, and writes an
     * {@code ACTION} Audit Record ({@code event: rule_revoked}), in the caller's transaction. Later Actions it would
     * have covered go through the gate without it.
     *
     * @param fixtureVersion the persona's fixture version, for the Audit Record
     * @return the stopped rule; empty if the rule is not the persona's
     * @throws RuleAlreadyStoppedException if the rule is already revoked
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    Optional<Rule> revokeRule(RequestContext context, PersonaScope scope, String fixtureVersion, UUID ruleId);

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
}
