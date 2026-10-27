package de.financetool.actions.domain;

import java.util.Locale;
import org.springframework.modulith.NamedInterface;

/** The regulatory class of an Action (FR-35). {@code insurance} and {@code legal} Actions are never executed. */
@NamedInterface("types")
public enum RegulatoryClass {
    PAYMENT, INVESTMENT, INSURANCE, LEGAL;

    /** The lower-case id stored and audited, e.g. {@code payment}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
