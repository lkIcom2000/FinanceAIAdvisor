package de.financetool.advisory.web;

import de.financetool.advisory.application.AnswerMessageUseCase;
import de.financetool.advisory.application.CalculateUseCase;
import de.financetool.advisory.application.PostMessageUseCase;
import de.financetool.advisory.application.ShowChatUseCase;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.CheckId;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.ProblemCode;
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import de.financetool.shared.web.FigureView;
import de.financetool.shared.web.ProblemDetailsAdvice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            List<String> suggestions,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The checks Fine's answer passed or failed, shown under \"Checks\"; empty on "
                            + "other messages and on fixed answers"))
            List<CheckView> checks,
            @Schema(description = "The case for a human advisor; only on answers passed to an advisor (ESCALATE)")
            HandoverView handover,
            @Schema(description = "\"Here's what I understood\": the values a calculation will run with; only on "
                    + "COMPUTE answers that ask before calculating") ProposalView proposal,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The figures a calculated answer used, shown as figure chips; empty on other "
                            + "messages"))
            List<FigureView> figures) {

        static MessageView of(ChatMessage message) {
            return new MessageView(message.id(), Author.valueOf(message.author().name()), message.text(),
                    message.outcome(), message.replyTo(), message.suggestions(),
                    message.checks().stream().map(CheckView::of).toList(),
                    message.handover() == null ? null : HandoverView.of(message.handover()),
                    message.proposal() == null ? null : ProposalView.of(message.proposal()),
                    message.figures().stream().map(FigureView::of).toList());
        }
    }

    @Schema(name = "ChatProposal", description = "The values a calculation will run with, shown before calculating")
    record ProposalView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OPEN: waiting for Calculate; "
                    + "CALCULATED: answered below; CANCELLED: a later question replaced it")
            ChatMessage.Proposal.Status status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProposalParameterView> parameters) {

        static ProposalView of(ChatMessage.Proposal proposal) {
            return new ProposalView(proposal.status(),
                    proposal.spec().parameters().stream().map(ProposalParameterView::of).toList());
        }
    }

    @Schema(name = "ChatProposalParameter", description = "One value of a proposed calculation")
    record ProposalParameterView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The key to change it by")
            String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Decimal string",
                    pattern = FigureView.DECIMAL) String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Unit unit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "QUESTION: from your question; "
                    + "ACCOUNT_DATA: from your account data; CHANGED: changed by you")
            SimulationSpec.Source source,
            @Schema(description = "The figure an account-data value is; only for ACCOUNT_DATA",
                    pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN) String provId) {

        static ProposalParameterView of(SimulationSpec.Parameter parameter) {
            return new ProposalParameterView(parameter.key(), parameter.label(), parameter.value().toPlainString(),
                    parameter.unit(), parameter.source(), parameter.provId());
        }
    }

    @Schema(name = "ChatHandover", description = "What the card shows of a case for a human advisor")
    record HandoverView(
            @Schema(description = "A plain reason class, e.g. \"because I couldn't confirm every figure in my "
                    + "answer\"; null when there is none") String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "OPEN: waiting for the advisor; CLOSED: answered") ChatMessage.Handover.Status status) {

        static HandoverView of(ChatMessage.Handover handover) {
            return new HandoverView(handover.reason(), handover.status());
        }
    }

    @Schema(name = "ChatCheck", description = "One check on Fine's answer")
    record CheckView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Shown only as a small caption")
            CheckId id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean passed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "What the check makes sure of, in "
                    + "plain words") String description) {

        static CheckView of(ChatMessage.Check check) {
            return new CheckView(check.id(), check.passed(), check.description());
        }
    }

    @Schema(name = "Chat", description = "The conversation, oldest message first")
    record ChatView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The name the greeting uses")
            String personaName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MessageView> messages) {
    }

    @Schema(name = "CalculateRequest", description = "Calculate a proposal, with the values the persona changed")
    record CalculateRequest(
            @ArraySchema(arraySchema = @Schema(description = "Only the changed values; empty or missing to calculate "
                    + "as proposed"), maxItems = 10)
            @Size(max = 10) List<@Valid ChangedValue> parameters) {

        CalculateRequest {
            parameters = parameters == null ? List.of() : parameters;
        }
    }

    @Schema(name = "ChangedValue", description = "A value the persona changed, in the parameter's unit (a rate as a "
            + "fraction: 0.04 = 4 %)")
    record ChangedValue(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = FigureView.DECIMAL)
            @NotBlank @Pattern(regexp = FigureView.DECIMAL) @Size(max = 32) String value) {
    }

    record PostMessageRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = PostMessageUseCase.MAX_LENGTH)
            @NotBlank @Size(max = PostMessageUseCase.MAX_LENGTH) String text) {
    }

    private final ShowChatUseCase showChat;
    private final PostMessageUseCase postMessage;
    private final AnswerMessageUseCase answerMessage;
    private final CalculateUseCase calculate;

    ChatController(ShowChatUseCase showChat, PostMessageUseCase postMessage, AnswerMessageUseCase answerMessage,
            CalculateUseCase calculate) {
        this.showChat = showChat;
        this.postMessage = postMessage;
        this.answerMessage = answerMessage;
        this.calculate = calculate;
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

    @Operation(summary = "Calculate a parameter proposal and return Fine's calculated answer",
            description = "Nothing is calculated before this call. A proposal that was already calculated returns "
                    + "that answer again.")
    @ApiResponse(responseCode = "200", description = "Fine's calculated answer")
    @ApiResponse(responseCode = "400", description = "A changed value is unknown or out of bounds")
    @ApiResponse(responseCode = "404", description = "No such proposal for this persona")
    @ApiResponse(responseCode = "409", description = "The proposal was cancelled by a later question")
    @ApiResponse(responseCode = "503", description = "The answer could not be finished; the proposal stays open")
    @PostMapping("/api/chat/messages/{id}/calculate")
    MessageView calculate(@Parameter(hidden = true) PersonaScope scope,
            @PathVariable @Parameter(description = "The proposal's id (a UUID)") String id,
            @Valid @RequestBody CalculateRequest request) {
        ErrorResponseException notFound = problem(HttpStatus.NOT_FOUND, "We couldn't find this message.", null);
        UUID messageId;
        try {
            messageId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound;
        }
        Map<String, BigDecimal> changed = new LinkedHashMap<>();
        for (ChangedValue value : request.parameters()) {
            if (changed.put(value.key(), new BigDecimal(value.value())) != null) {
                throw problem(HttpStatus.BAD_REQUEST, "Each value can be changed only once.",
                        ProblemCode.VALIDATION_FAILED);
            }
        }
        return calculate.calculate(scope, messageId, changed).map(MessageView::of).orElseThrow(() -> notFound);
    }

    @ExceptionHandler(CalculateUseCase.ProposalClosedException.class)
    ProblemDetail proposalClosed() {
        return problem(HttpStatus.CONFLICT, "This calculation was replaced by your next question.",
                ProblemCode.PROPOSAL_CLOSED).getBody();
    }

    @ExceptionHandler(CalculateUseCase.InvalidValuesException.class)
    ProblemDetail invalidValues(CalculateUseCase.InvalidValuesException ex) {
        return problem(HttpStatus.BAD_REQUEST, String.join(" ", ex.problems()), ProblemCode.VALIDATION_FAILED)
                .getBody();
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
