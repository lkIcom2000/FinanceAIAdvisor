package de.financetool.advisory.domain;

import java.util.Optional;

/**
 * The plain reason class an {@code ESCALATE} card may show (UX-DR26): derived from the route only, never the draft
 * or a check's reason code. Routes without a plain class show none.
 */
public enum HandoverReason {

    RULE_NOT_CONFIRMED("because a rule this answer depends on isn't confirmed yet"),
    FIGURES_NOT_CONFIRMED("because I couldn't confirm every figure in my answer");

    private final String text;

    HandoverReason(String text) {
        this.text = text;
    }

    /** The phrase the card shows. */
    public String text() {
        return text;
    }

    /** The reason class of a route, if it has one. */
    public static Optional<HandoverReason> of(String route) {
        return switch (route) {
            case "invalid_rule_parameter" -> Optional.of(RULE_NOT_CONFIRMED);
            case "number_mismatch_after_regeneration" -> Optional.of(FIGURES_NOT_CONFIRMED);
            default -> Optional.empty();
        };
    }

    /** The phrase for a stored reason class name; {@code null} for none or an unknown name. */
    public static String textOf(String name) {
        if (name == null) {
            return null;
        }
        for (HandoverReason reason : values()) {
            if (reason.name().equals(name)) {
                return reason.text;
            }
        }
        return null;
    }
}
