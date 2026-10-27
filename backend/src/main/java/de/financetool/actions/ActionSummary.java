package de.financetool.actions;

import de.financetool.actions.domain.ActionState;
import java.util.Objects;

/**
 * Module API: an Action's current state and customer reason.
 *
 * @param reason why the Action needs the user's OK or was refused, in plain words; {@code null} if none
 */
public record ActionSummary(ActionState state, String reason) {

    public ActionSummary {
        Objects.requireNonNull(state, "state");
    }
}
