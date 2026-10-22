package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.shared.PersonaScope;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step one of a turn: saves the user's message in its own transaction, so it stays saved whatever happens to the
 * answer. Length and blankness are validated by the controller.
 */
@Service
public class PostMessageUseCase {

    public static final int MAX_LENGTH = 1000;

    private final ChatRepository chats;

    PostMessageUseCase(ChatRepository chats) {
        this.chats = chats;
    }

    @Transactional
    public ChatMessage post(PersonaScope scope, String text) {
        ChatMessage message = new ChatMessage(UUID.randomUUID(), ChatMessage.Author.USER, text, null, null);
        chats.insert(scope, chats.conversation(scope), message);
        return message;
    }
}
