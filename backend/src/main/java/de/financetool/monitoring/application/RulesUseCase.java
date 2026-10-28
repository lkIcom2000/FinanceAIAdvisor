package de.financetool.monitoring.application;

import de.financetool.actions.Rule;
import de.financetool.actions.RuleCheck;
import de.financetool.actions.RuleSetup;
import de.financetool.actions.Rules;
import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Your rules" on the Home tab and the rule setup dialog (FR-36, FR-7) at the persona's simulated date. Rules are
 * only written by {@link Actions}; starting one is one transaction (row and Audit Record together, or nothing).
 */
@Service
public class RulesUseCase {

    private final RequestContextFactory contexts;
    private final PersonaDirectory personas;
    private final Rules rules;

    RulesUseCase(RequestContextFactory contexts, PersonaDirectory personas, Rules rules) {
        this.contexts = contexts;
        this.personas = personas;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<Rule> rules(PersonaScope scope) {
        return rules.rules(contexts.forPersona(scope, null), scope);
    }

    /** The templates with the Core's suggested limits; the suggestions are stored as evidence for "Why?". */
    @Transactional
    public List<RuleSetup> setup(PersonaScope scope) {
        return rules.ruleSetup(contexts.forPersona(scope, null), scope);
    }

    /** @throws de.financetool.actions.InvalidRuleException if the limits can't be used */
    public RuleCheck check(RuleTemplate template, RuleBounds bounds) {
        return rules.checkRule(template, bounds);
    }

    /**
     * @throws de.financetool.actions.InvalidRuleException if the limits can't be used
     * @throws de.financetool.actions.RuleAlreadyActiveException if a rule of the template is already active
     */
    @Transactional
    public Rule start(PersonaScope scope, RuleTemplate template, RuleBounds bounds) {
        return rules.createRule(contexts.forPersona(scope, null), scope, personas.fixtureVersion(scope), template,
                bounds);
    }

    /**
     * Stops a rule (FR-38) in one transaction: revocation date and Audit Record together, or nothing.
     *
     * @return empty if the rule is not the persona's
     * @throws de.financetool.actions.RuleAlreadyStoppedException if the rule is already stopped
     */
    @Transactional
    public Optional<Rule> revoke(PersonaScope scope, UUID ruleId) {
        return rules.revokeRule(contexts.forPersona(scope, null), scope, personas.fixtureVersion(scope), ruleId);
    }
}
