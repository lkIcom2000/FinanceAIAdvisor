package de.financetool.actions;

import de.financetool.actions.domain.ActionState;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Module API: an Action's current state and customer reason.
 *
 * @param reason why the Action needs the user's OK or was refused, in plain words; {@code null} if none
 * @param underRule whether Fine executed the Action on its own within a rule
 * @param undoUntil the last date the Action can be undone, for an Action Fine executed within a rule; else {@code null}
 */
public record ActionSummary(ActionState state, String reason, boolean underRule, LocalDate undoUntil) {

    public ActionSummary {
        Objects.requireNonNull(state, "state");
    }
}
