package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persona reset of the advisory module: the fixtures hold no chat, so the persona's chat rows are deleted. */
@Service
class AdvisoryResetter implements PersonaResettable {

    private final ChatRepository chats;

    AdvisoryResetter(ChatRepository chats) {
        this.chats = chats;
    }

    @Override
    public String module() {
        return "advisory";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        chats.deletePersonaRows(personaId);
    }
}
