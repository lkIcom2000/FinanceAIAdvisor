package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The persona's conversation: every saved message in order with its handover, and the name the greeting uses. */
@Service
public class ShowChatUseCase {

    public record Chat(String personaName, List<ChatMessage> messages) {
    }

    private final PersonaDirectory personas;
    private final ChatRepository chats;
    private final Handovers handovers;

    ShowChatUseCase(PersonaDirectory personas, ChatRepository chats, Handovers handovers) {
        this.personas = personas;
        this.chats = chats;
        this.handovers = handovers;
    }

    @Transactional(readOnly = true)
    public Chat show(PersonaScope scope) {
        return new Chat(personas.find(scope).displayName(), handovers.attach(scope, chats.messages(scope)));
    }

    /** The demands-and-needs statement of the persona's latest answer that has one (UX-DR44). */
    @Transactional(readOnly = true)
    public Optional<ChatMessage.NeedsStatement> needsStatement(PersonaScope scope) {
        return chats.messages(scope).reversed().stream().map(ChatMessage::needsStatement).filter(Objects::nonNull)
                .findFirst();
    }
}
