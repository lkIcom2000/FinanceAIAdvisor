package de.financetool.actions;

import java.util.List;

/** Module API: the "Check and confirm" step of a rule (UX-DR23), built from limits that passed the checks. */
public record RuleCheck(String summary, List<String> will, List<String> wont, String undoAndStop) {

    public RuleCheck {
        will = List.copyOf(will);
        wont = List.copyOf(wont);
    }
}
