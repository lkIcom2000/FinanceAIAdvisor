package de.financetool.actions;

import de.financetool.actions.domain.RuleTemplate;
import de.financetool.shared.domain.SourcedNumber;
import java.util.Objects;

/**
 * Module API: one rule template in the setup dialog (UX-DR23) with the limits the Core suggests.
 *
 * @param alreadyActive whether the persona already has an active rule of this template
 * @param source where the suggested limits come from, in customer words
 */
public record RuleSetup(RuleTemplate template, String title, String fromName, String toName, boolean alreadyActive,
        SourcedNumber maxPerAction, SourcedNumber maxPerMonth, SourcedNumber floor, String source) {

    public RuleSetup {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(maxPerAction, "maxPerAction");
        Objects.requireNonNull(maxPerMonth, "maxPerMonth");
        Objects.requireNonNull(floor, "floor");
    }
}
