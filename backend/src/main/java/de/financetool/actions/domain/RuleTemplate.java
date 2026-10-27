package de.financetool.actions.domain;

import org.springframework.modulith.NamedInterface;

/**
 * The two rule (Mandate) templates (FR-36), one per Action that may reach L3 (FR-37). Each fixes the direction of the
 * transfer: the safety cushion moves money from the overnight deposit to the current account, the top-up the other way.
 */
@NamedInterface("types")
public enum RuleTemplate {
    SAFETY_CUSHION(ActionType.SHORTFALL_TRANSFER, "Keep a safety cushion in my current account", true),
    EMERGENCY_FUND_TOP_UP(ActionType.EF_TOP_UP, "Top up my emergency fund", false);

    private final ActionType actionType;
    private final String title;
    private final boolean fromOvernight;

    RuleTemplate(ActionType actionType, String title, boolean fromOvernight) {
        this.actionType = actionType;
        this.title = title;
        this.fromOvernight = fromOvernight;
    }

    /** The Action a rule of this template covers. */
    public ActionType actionType() {
        return actionType;
    }

    /** The customer title of the template card. */
    public String title() {
        return title;
    }

    /** Whether money moves out of the overnight deposit (else out of the current account). */
    public boolean fromOvernight() {
        return fromOvernight;
    }
}
