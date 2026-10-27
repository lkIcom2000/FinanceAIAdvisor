package de.financetool.actions;

import de.financetool.actions.domain.ActionState;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Module API: the only creator of Actions (AD-10). Chat never calls it (AD-3). */
public interface Actions {

    /**
     * Runs the Autonomy Gate on the Action against one bank snapshot at {@code context.asOf()}, stores the Action in
     * the state its level allows (L0 informed, L1 recommended, L2 awaiting confirmation) and writes an {@code ACTION}
     * Audit Record, in the caller's transaction. Nothing is executed. Without a valid Rule Parameter for the
     * emergency-fund target the Action is stored {@code ESCALATED} without a level.
     *
     * @param fixtureVersion the persona's fixture version, for the Audit Record
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    ActionProposal propose(RequestContext context, PersonaScope scope, String fixtureVersion, ProposedAction action);

    /**
     * The current states of the scope's Actions with these ids, in the caller's transaction; an id that is not one of
     * the persona's Actions is left out.
     */
    Map<UUID, ActionState> states(PersonaScope scope, Collection<UUID> actionIds);
}
