package de.financetool.actions;

/** Module API: the Action exists but can't be undone (any more); the message is the customer text. */
public final class NotUndoableException extends RuntimeException {

    public NotUndoableException(String message) {
        super(message);
    }
}
