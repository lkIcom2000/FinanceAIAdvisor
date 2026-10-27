package de.financetool.actions;

/** Module API: the rule can't be set up as asked; the message is in customer words. */
public final class InvalidRuleException extends RuntimeException {

    public InvalidRuleException(String message) {
        super(message);
    }
}
