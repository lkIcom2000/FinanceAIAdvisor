package de.financetool.actions.application;

import de.financetool.actions.adapter.ActionRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persona reset of the actions module: the fixtures hold no Actions, so the persona's Actions are deleted. */
@Service
class ActionResetter implements PersonaResettable {

    private final ActionRepository actions;

    ActionResetter(ActionRepository actions) {
        this.actions = actions;
    }

    @Override
    public String module() {
        return "actions";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        actions.deletePersonaRows(personaId);
    }
}
