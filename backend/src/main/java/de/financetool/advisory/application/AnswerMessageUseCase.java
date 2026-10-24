package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step two of a turn: runs the pipeline for a saved user message and releases one complete, audited answer, all in
 * this one transaction (AD-7). An already answered message returns its stored answer without a second turn.
 */
@Service
public class AnswerMessageUseCase {

    /** The turn could not be finished; everything it wrote is rolled back, the question stays saved. */
    public static class TurnFailedException extends RuntimeException {
        TurnFailedException(Throwable cause) {
            super("The turn failed", cause);
        }
    }

    private final RequestContextFactory contexts;
    private final PersonaDirectory personas;
    private final ChatRepository chats;
    private final TurnOrchestrator orchestrator;
    private final Handovers handovers;

    AnswerMessageUseCase(RequestContextFactory contexts, PersonaDirectory personas, ChatRepository chats,
            TurnOrchestrator orchestrator, Handovers handovers) {
        this.contexts = contexts;
        this.personas = personas;
        this.chats = chats;
        this.orchestrator = orchestrator;
        this.handovers = handovers;
    }

    /**
     * @return empty if the scope's persona has no user message with this id (another persona's id included)
     * @throws TurnFailedException if the model, a write or the audit fails
     */
    @Transactional
    public Optional<ChatMessage> answer(PersonaScope scope, UUID messageId) {
        Optional<ChatMessage> question = chats.find(scope, messageId)
                .filter(message -> message.author() == ChatMessage.Author.USER);
        if (question.isEmpty()) {
            return Optional.empty();
        }
        Optional<ChatMessage> stored = chats.findReply(scope, messageId);
        if (stored.isPresent()) {
            return Optional.of(handovers.attach(scope, stored.get()));
        }
        RequestContext context = contexts.forPersona(scope, null);
        String fixtureVersion = personas.fixtureVersion(scope);
        try {
            return Optional.of(orchestrator.run(context, scope, question.get(), fixtureVersion));
        } catch (RuntimeException e) {
            throw new TurnFailedException(e);
        }
    }
}
