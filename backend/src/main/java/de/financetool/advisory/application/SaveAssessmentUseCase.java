package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.personas.PersonaDirectory;
import de.financetool.profile.AssessmentFields;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Save my answers on an assessment card (FR-17, AD-24): the answers are stored only through the profile module's
 * confirm control, then the card's question runs again on the stored profile and its answer is released as the
 * card's reply, all in this one transaction. A card that was already saved returns its stored reply.
 */
@Service
public class SaveAssessmentUseCase {

    /** The card was cancelled by a later question; nothing is stored. */
    public static class AssessmentClosedException extends RuntimeException {
        AssessmentClosedException() {
            super("The assessment is no longer open");
        }
    }

    /** An answer is missing, not on the card or does not fit its field; the problems are plain sentences. */
    public static class InvalidAnswersException extends RuntimeException {
        private final List<String> problems;

        InvalidAnswersException(List<String> problems) {
            super(String.join(" ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    private final RequestContextFactory contexts;
    private final PersonaDirectory personas;
    private final ChatRepository chats;
    private final Profiles profiles;
    private final TurnOrchestrator orchestrator;
    private final Handovers handovers;

    SaveAssessmentUseCase(RequestContextFactory contexts, PersonaDirectory personas, ChatRepository chats,
            Profiles profiles, TurnOrchestrator orchestrator, Handovers handovers) {
        this.contexts = contexts;
        this.personas = personas;
        this.chats = chats;
        this.profiles = profiles;
        this.orchestrator = orchestrator;
        this.handovers = handovers;
    }

    /**
     * @param answers every card field's answer as typed (a pre-filled value the persona kept is sent as shown)
     * @return empty if the scope's persona has no assessment message with this id
     * @throws AssessmentClosedException if the card was cancelled
     * @throws InvalidAnswersException if an answer is missing, not on the card or does not fit its field
     * @throws AnswerMessageUseCase.TurnFailedException if the model, a write or the audit fails
     */
    @Transactional
    public Optional<ChatMessage> save(PersonaScope scope, UUID cardId, Map<String, String> answers) {
        // A second Save of the same card (double click, retry) waits here and then finds the first one's reply.
        chats.lock(scope, cardId);
        Optional<ChatMessage> card = chats.find(scope, cardId).filter(m -> m.assessment() != null);
        if (card.isEmpty()) {
            return Optional.empty();
        }
        Optional<ChatMessage> stored = chats.findReply(scope, cardId);
        if (stored.isPresent()) {
            return Optional.of(handovers.attach(scope, stored.get()));
        }
        if (card.get().assessment().status() != ChatMessage.Assessment.Status.OPEN) {
            throw new AssessmentClosedException();
        }
        RequestContext context = contexts.forPersona(scope, null);
        String fixtureVersion = personas.fixtureVersion(scope);
        try {
            profiles.confirm(scope, values(card.get().assessment(), answers), confirmedAt(context));
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswersException(List.of(e.getMessage()));
        }
        try {
            ChatMessage question = question(scope, card.get());
            return Optional.of(orchestrator.resume(context, scope, question,
                    new TurnOrchestrator.SavedAssessment(card.get()), fixtureVersion));
        } catch (AssessmentClosedException e) {
            // A concurrent question or Save closed it after the check above; the transaction rolls back.
            throw e;
        } catch (RuntimeException e) {
            throw new AnswerMessageUseCase.TurnFailedException(e);
        }
    }

    /**
     * The user's question a card was opened for: a card that answers a saved card (still not enough known) replies
     * to that card, so the chain is followed back to the user message.
     */
    private ChatMessage question(PersonaScope scope, ChatMessage card) {
        ChatMessage message = card;
        for (int step = 0; step < MAX_CHAIN && message.author() != ChatMessage.Author.USER; step++) {
            message = chats.find(scope, message.replyTo()).orElseThrow();
        }
        if (message.author() != ChatMessage.Author.USER) {
            throw new IllegalStateException("No question found for the card");
        }
        return message;
    }

    private static final int MAX_CHAIN = 20;

    /** The persona's simulated date as the confirmation time (AD-8: no wall clock in the application). */
    static Instant confirmedAt(RequestContext context) {
        return context.asOf().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The stored values for the answers: exactly the card's fields, each fitting its field. */
    static Map<String, Object> values(ChatMessage.Assessment card, Map<String, String> answers) {
        List<String> problems = new ArrayList<>();
        answers.keySet().stream().filter(key -> !card.keys().contains(key)).findFirst()
                .ifPresent(key -> problems.add("This form has no question called " + key + "."));
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : card.keys()) {
            try {
                values.put(key, AssessmentFields.parse(key, answers.get(key)));
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidAnswersException(problems);
        }
        return values;
    }
}
