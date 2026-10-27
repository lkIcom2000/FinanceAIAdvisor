package de.financetool.actions;

/** Module API: the persona already has an active rule of this template. */
public final class RuleAlreadyActiveException extends RuntimeException {

    public RuleAlreadyActiveException() {
        super("an active rule of this template exists");
    }
}
