package de.financetool.knowledge;

/** A Rule Parameter is unknown or has no version valid at the as-of date; a turn that needs it escalates (FR-13). */
public class NoValidRuleParameterException extends IllegalStateException {

    public NoValidRuleParameterException(String message) {
        super(message);
    }
}
