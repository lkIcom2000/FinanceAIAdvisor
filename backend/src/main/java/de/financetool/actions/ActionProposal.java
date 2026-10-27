package de.financetool.actions;

import de.financetool.actions.domain.ActionState;
import de.financetool.actions.domain.AutonomyLevel;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Module API: the stored Action and the gate's decision on it.
 *
 * @param level {@code null} only for an {@link ActionState#ESCALATED} Action
 */
public record ActionProposal(UUID actionId, ActionState state, AutonomyLevel level, List<String> cappingCriteria) {

    public ActionProposal {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(state, "state");
        cappingCriteria = List.copyOf(cappingCriteria);
    }
}
