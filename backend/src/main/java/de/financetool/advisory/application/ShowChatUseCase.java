package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The persona's conversation: every saved message in order, and the name the greeting uses. */
@Service
public class ShowChatUseCase {

    public record Chat(String personaName, List<ChatMessage> messages) {
    }

    private final PersonaDirectory personas;
    private final ChatRepository chats;

    ShowChatUseCase(PersonaDirectory personas, ChatRepository chats) {
        this.personas = personas;
        this.chats = chats;
    }

    @Transactional(readOnly = true)
    public Chat show(PersonaScope scope) {
        return new Chat(personas.find(scope).displayName(), chats.messages(scope));
    }
}
