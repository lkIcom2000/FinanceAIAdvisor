package de.financetool.actions;

import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Module API: an active rule (Mandate) on the Home tab (UX-DR22).
 *
 * @param fromName the source account's name, or {@code null} if it no longer exists
 * @param toName the target account's name, or {@code null} if it no longer exists
 * @param activeSince the simulated date the rule was started
 * @param actionCount the number of Actions made under the rule
 */
public record Rule(UUID id, RuleTemplate template, String summary, String fromName, String toName, RuleBounds bounds,
        LocalDate activeSince, int actionCount) {
}
