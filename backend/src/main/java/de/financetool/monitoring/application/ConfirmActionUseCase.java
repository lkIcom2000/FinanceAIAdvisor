package de.financetool.monitoring.application;

import de.financetool.actions.ActionConfirmation;
import de.financetool.actions.ActionReview;
import de.financetool.actions.Actions;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Review and confirm" on a Notification (FR-39, FR-40) at the persona's simulated date: the L2 dialog's content, and
 * the confirmation in one transaction (re-check, booking, row update and Audit Record together, or nothing). The
 * Action itself is only changed by {@link Actions}.
 */
@Service
public class ConfirmActionUseCase {

    private final RequestContextFactory contexts;
    private final PersonaDirectory personas;
    private final Actions actions;

    ConfirmActionUseCase(RequestContextFactory contexts, PersonaDirectory personas, Actions actions) {
        this.contexts = contexts;
        this.personas = personas;
        this.actions = actions;
    }

    /**
     * @return empty if the persona has no Action with this id
     * @throws de.financetool.actions.NotAwaitingConfirmationException if it is not a transfer awaiting confirmation
     */
    @Transactional(readOnly = true)
    public Optional<ActionReview> review(PersonaScope scope, UUID actionId) {
        return actions.review(contexts.forPersona(scope, null), scope, actionId);
    }

    /**
     * @return empty if the persona has no Action with this id
     * @throws de.financetool.actions.NotAwaitingConfirmationException if it is not (or no longer) a transfer awaiting
     *         confirmation
     */
    @Transactional
    public Optional<ActionConfirmation> confirm(PersonaScope scope, UUID actionId) {
        return actions.confirm(contexts.forPersona(scope, null), scope, personas.fixtureVersion(scope), actionId);
    }
}
