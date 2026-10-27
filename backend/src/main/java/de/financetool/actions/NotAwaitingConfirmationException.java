package de.financetool.actions;

/** Module API: the Action exists but is not (or no longer) a transfer waiting for the user's OK. */
public final class NotAwaitingConfirmationException extends RuntimeException {

    public NotAwaitingConfirmationException() {
        super("the Action is not a transfer awaiting confirmation");
    }
}
