package de.financetool.actions;

/** Module API: the rule has already been stopped (revoked). */
public final class RuleAlreadyStoppedException extends RuntimeException {

    public RuleAlreadyStoppedException() {
        super("the rule is already revoked");
    }
}
