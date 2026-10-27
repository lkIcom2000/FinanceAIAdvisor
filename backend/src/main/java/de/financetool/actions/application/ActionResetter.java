package de.financetool.actions.application;

import de.financetool.actions.adapter.ActionRepository;
import de.financetool.actions.adapter.MandateRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persona reset of the actions module: the fixtures hold no Actions or rules, so the persona's Actions and then its
 * rules are deleted.
 */
@Service
class ActionResetter implements PersonaResettable {

    private final ActionRepository actions;
    private final MandateRepository mandates;

    ActionResetter(ActionRepository actions, MandateRepository mandates) {
        this.actions = actions;
        this.mandates = mandates;
    }

    @Override
    public String module() {
        return "actions";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        actions.deletePersonaRows(personaId);
        mandates.deletePersonaRows(personaId);
    }
}
