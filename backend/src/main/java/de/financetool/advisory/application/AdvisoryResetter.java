package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.adapter.NluCacheRepository;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persona reset of the advisory module: the fixtures hold no chat, so the persona's chat rows and cached NLU
 * extractions are deleted.
 */
@Service
class AdvisoryResetter implements PersonaResettable {

    private final ChatRepository chats;
    private final NluCacheRepository nluCache;

    AdvisoryResetter(ChatRepository chats, NluCacheRepository nluCache) {
        this.chats = chats;
        this.nluCache = nluCache;
    }

    @Override
    public String module() {
        return "advisory";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(PersonaId personaId) {
        chats.deletePersonaRows(personaId);
        nluCache.deletePersonaRows(personaId);
    }
}
