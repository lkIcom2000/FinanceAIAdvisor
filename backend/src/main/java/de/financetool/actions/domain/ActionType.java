package de.financetool.actions.domain;

import org.springframework.modulith.NamedInterface;

/**
 * The kinds of Action Fine can propose. Each carries its regulatory class, whether it can be reversed, and whether
 * it may reach L3 at all (FR-35: only {@code EF_TOP_UP} and {@code SHORTFALL_TRANSFER}). Story 8.2 and AD-27 add more.
 */
@NamedInterface("types")
public enum ActionType {
    /** Move money from the overnight deposit to the current account before a forecast shortfall. */
    SHORTFALL_TRANSFER(RegulatoryClass.PAYMENT, true, true),
    /** Move money into the overnight deposit to bring the emergency fund up to its target. */
    EF_TOP_UP(RegulatoryClass.PAYMENT, true, true);

    private final RegulatoryClass regulatoryClass;
    private final boolean reversible;
    private final boolean l3Eligible;

    ActionType(RegulatoryClass regulatoryClass, boolean reversible, boolean l3Eligible) {
        this.regulatoryClass = regulatoryClass;
        this.reversible = reversible;
        this.l3Eligible = l3Eligible;
    }

    public RegulatoryClass regulatoryClass() {
        return regulatoryClass;
    }

    public boolean reversible() {
        return reversible;
    }

    /** Whether the gate may grant L3 when every criterion is met. */
    public boolean l3Eligible() {
        return l3Eligible;
    }
}
