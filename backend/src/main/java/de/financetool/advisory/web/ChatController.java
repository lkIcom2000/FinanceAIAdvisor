package de.financetool.advisory.web;

import de.financetool.advisory.application.AnswerMessageUseCase;
import de.financetool.advisory.application.PostMessageUseCase;
import de.financetool.advisory.application.ShowChatUseCase;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.ProblemCode;
import de.financetool.shared.web.ProblemDetailsAdvice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The chat with Fine (FR-28). A turn has two steps: posting saves the question, answering runs the turn and returns
 * one complete, checked and audited answer (no streaming). The persona comes only from the session; another
 * persona's message id answers exactly like an unknown one.
 */
@Tag(name = "chat", description = "The chat with Fine")
@RestController
class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** Problems name the collection, never the requested id. */
    private static final URI INSTANCE = URI.create("/api/chat/messages");

    enum Author {
        USER, AGENT, ADVISOR
    }

    @Schema(name = "ChatMessage", description = "One message of the conversation")
    record MessageView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Author author,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
            @Schema(description = "How the turn ended; only on Fine's answers") ResponseOutcome outcome,
            @Schema(description = "The question an answer replies to; only on Fine's answers") UUID replyTo,
            @ArraySchema(maxItems = ChatMessage.MAX_SUGGESTIONS, arraySchema = @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Suggestion chips of Fine's answer: question texts that send themselves; "
                            + "empty on other messages"))
            List<String> suggestions) {

        static MessageView of(ChatMessage message) {
            return new MessageView(message.id(), Author.valueOf(message.author().name()), message.text(),
                    message.outcome(), message.replyTo(), message.suggestions());
        }
    }

    @Schema(name = "Chat", description = "The conversation, oldest message first")
    record ChatView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The name the greeting uses")
            String personaName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MessageView> messages) {
    }

    record PostMessageRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = PostMessageUseCase.MAX_LENGTH)
            @NotBlank @Size(max = PostMessageUseCase.MAX_LENGTH) String text) {
    }

    private final ShowChatUseCase showChat;
    private final PostMessageUseCase postMessage;
    private final AnswerMessageUseCase answerMessage;

    ChatController(ShowChatUseCase showChat, PostMessageUseCase postMessage, AnswerMessageUseCase answerMessage) {
        this.showChat = showChat;
        this.postMessage = postMessage;
        this.answerMessage = answerMessage;
    }

    @Operation(summary = "The conversation with every saved message")
    @GetMapping("/api/chat")
    ChatView chat(@Parameter(hidden = true) PersonaScope scope) {
        ShowChatUseCase.Chat chat = showChat.show(scope);
        return new ChatView(chat.personaName(), chat.messages().stream().map(MessageView::of).toList());
    }

    @Operation(summary = "Save a question; answer it with the answer call")
    @ApiResponse(responseCode = "201", description = "The saved question")
    @ApiResponse(responseCode = "400", description = "Blank or longer than 1,000 characters")
    @PostMapping("/api/chat/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView postMessage(@Parameter(hidden = true) PersonaScope scope,
            @Valid @RequestBody PostMessageRequest request) {
        return MessageView.of(postMessage.post(scope, request.text()));
    }

    @Operation(summary = "Run the turn for a saved question and return Fine's complete answer",
            description = "A question that already has an answer returns that answer again.")
    @ApiResponse(responseCode = "200", description = "Fine's answer")
    @ApiResponse(responseCode = "404", description = "No such question for this persona")
    @ApiResponse(responseCode = "503", description = "The answer could not be finished; the question stays saved")
    @PostMapping("/api/chat/messages/{id}/answer")
    MessageView answerMessage(@Parameter(hidden = true) PersonaScope scope,
            @PathVariable @Parameter(description = "The question's id (a UUID)") String id) {
        ErrorResponseException notFound = problem(HttpStatus.NOT_FOUND, "We couldn't find this message.", null);
        UUID messageId;
        try {
            messageId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            // A malformed id answers like an unknown one and is never echoed back.
            throw notFound;
        }
        return answerMessage.answer(scope, messageId).map(MessageView::of).orElseThrow(() -> notFound);
    }

    @ExceptionHandler(AnswerMessageUseCase.TurnFailedException.class)
    ProblemDetail turnFailed(AnswerMessageUseCase.TurnFailedException ex) {
        log.error("Chat turn failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
                "Something went wrong on our side, so I couldn't finish the answer. Your question is saved.",
                ProblemCode.TURN_FAILED).getBody();
    }

    private static ErrorResponseException problem(HttpStatus status, String detail, ProblemCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(INSTANCE);
        if (code != null) {
            problem.setProperty(ProblemDetailsAdvice.CODE_PROPERTY, code);
        }
        return new ErrorResponseException(status, problem, null);
    }
}
