package de.financetool.advisory.web;

import de.financetool.advisory.application.AnswerMessageUseCase;
import de.financetool.advisory.application.CalculateUseCase;
import de.financetool.advisory.application.PostMessageUseCase;
import de.financetool.advisory.application.SaveAssessmentUseCase;
import de.financetool.advisory.application.ShowChatUseCase;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.CheckId;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.advisory.domain.ToolPlan;
import de.financetool.profile.AssessmentFields;
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
import jakarta.validation.constraints.NotNull;
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
import org.springframework.http.ResponseEntity;
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
            List<FigureView> figures,
            @Schema(description = "The false assumption a question makes, in one sentence; only on DETECT_TRAP "
                    + "answers") String premise,
            @Schema(description = "The form with every profile question still open; only on ASK_PROFILE answers")
            AssessmentView assessment,
            @Schema(description = "\"Why this fits you\": the Suitability Statement; only on RECOMMEND_CATEGORY "
                    + "answers") RecommendationView recommendation,
            @Schema(description = "The demands-and-needs statement; only on RECOMMEND_CATEGORY answers to an "
                    + "insurance question") NeedsStatementView needsStatement,
            @Schema(description = "The range chart of a calculated what-if; only on COMPUTE answers to a what-if")
            RangeChartView range) {

        static MessageView of(ChatMessage message) {
            return new MessageView(message.id(), Author.valueOf(message.author().name()), message.text(),
                    message.outcome(), message.replyTo(), message.suggestions(),
                    message.checks().stream().map(CheckView::of).toList(),
                    message.handover() == null ? null : HandoverView.of(message.handover()),
                    message.proposal() == null ? null : ProposalView.of(message.proposal()),
                    message.figures().stream().map(FigureView::of).toList(), message.premise(),
                    message.assessment() == null ? null : AssessmentView.of(message.assessment()),
                    message.recommendation() == null ? null : RecommendationView.of(message.recommendation()),
                    message.needsStatement() == null ? null : NeedsStatementView.of(message.needsStatement()),
                    message.range() == null ? null : RangeChartView.of(message.range()));
        }
    }

    @Schema(name = "ChatRangeChart", description = "Where a what-if may lead, from Fine's calculation engine: a "
            + "range, not a promise")
    record RangeChartView(
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The percentiles at the end of each year"))
            List<RangePointView> points,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Return model, costs, inflation, horizon and subsidy rule"))
            List<String> assumptions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN,
                    description = "Key of the projection's provenance (its middle outcome), for \"Why?\"")
            String provId) {

        static RangeChartView of(ChatMessage.RangeChart range) {
            return new RangeChartView(range.points().stream()
                    .map(p -> new RangePointView(p.year(), p.p10().toPlainString(), p.p50().toPlainString(),
                            p.p90().toPlainString()))
                    .toList(), range.assumptions(), range.provId());
        }
    }

    @Schema(name = "ChatRangePoint", description = "A what-if's percentiles at the end of one year (EUR)")
    record RangePointView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int year,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = FigureView.DECIMAL) String p10,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = FigureView.DECIMAL) String p50,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = FigureView.DECIMAL) String p90) {
    }

    @Schema(name = "NeedsStatement", description = "The demands-and-needs statement for your insurance, from Fine's "
            + "calculation engine, your answers and the contracts in your banking data; it recommends no product")
    record NeedsStatementView(
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Liability, occupational disability and cover held twice, in this order"))
            List<NeedsAreaView> areas,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The facts about you the statement used"))
            List<RecommendationFactView> facts,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Contracts per cover and risks covered more than once"))
            List<FigureView> figures) {

        static NeedsStatementView of(ChatMessage.NeedsStatement statement) {
            return new NeedsStatementView(statement.areas().stream()
                    .map(a -> new NeedsAreaView(a.area(), a.need(), a.have(), a.check())).toList(),
                    statement.facts().stream().map(f -> new RecommendationFactView(f.label(), f.value())).toList(),
                    statement.figures().stream().map(FigureView::of).toList());
        }
    }

    @Schema(name = "NeedsArea", description = "One area of the demands-and-needs statement")
    record NeedsAreaView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The area in plain words") String area,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Why the cover matters for you")
            String need,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "What your banking data shows")
            String have,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "What to check, and with whom")
            String check) {
    }

    @Schema(name = "ChatRecommendation", description = "The Suitability Statement of a Category Recommendation, "
            + "from Fine's calculation engine and your profile")
    record RecommendationView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The category in plain words")
            String category,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Where the plan conflicts with your profile; empty if it fits"))
            List<String> conflicts,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The profile facts the selection used"))
            List<RecommendationFactView> facts,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The figures behind the selection"))
            List<FigureView> figures,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The main risks"))
            List<String> risks,
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The ex-ante costs; empty for a recommendation without investment"))
            List<FigureView> costs) {

        static RecommendationView of(ChatMessage.Recommendation recommendation) {
            return new RecommendationView(recommendation.label(),
                    recommendation.conflicts().stream().map(ResponseTemplates::plainConflict).toList(),
                    recommendation.facts().stream().map(f -> new RecommendationFactView(f.label(), f.value()))
                            .toList(),
                    recommendation.figures().stream().map(FigureView::of).toList(), recommendation.risks(),
                    recommendation.costs().stream().map(FigureView::of).toList());
        }
    }

    @Schema(name = "ChatRecommendationFact", description = "One profile fact a recommendation used")
    record RecommendationFactView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String value) {
    }

    @Schema(name = "ChatAssessment", description = "The assessment card: every open profile question in one form")
    record AssessmentView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OPEN: waiting for Save my answers; "
                    + "SAVED: answered below; CANCELLED: a later question replaced it")
            ChatMessage.Assessment.Status status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AssessmentFieldView> fields) {

        static AssessmentView of(ChatMessage.Assessment assessment) {
            return new AssessmentView(assessment.status(),
                    assessment.fields().stream().map(AssessmentFieldView::of).toList());
        }
    }

    @Schema(name = "ChatAssessmentField", description = "One question of the assessment card")
    record AssessmentFieldView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The key to answer it by")
            String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "CHOICE: choice chips; EUR: amount "
                    + "in euros; NUMBER: whole number; SLIDER: options from the lower to the upper end; TEXT: short "
                    + "text") AssessmentFields.Input input,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The choices of CHOICE and SLIDER; "
                    + "empty otherwise") List<AssessmentOptionView> options,
            @Schema(description = "The unit after a NUMBER field, e.g. years") String unit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Smallest NUMBER or EUR value")
            int min,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Largest NUMBER or EUR value, or the "
                    + "longest TEXT") int max,
            @Schema(description = "The value from your account data, as a decimal string; only on pre-filled "
                    + "questions", pattern = FigureView.DECIMAL) String value,
            @Schema(description = "The figure a pre-filled value is; only on pre-filled questions",
                    pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN) String provId) {

        static AssessmentFieldView of(ChatMessage.Assessment.Field field) {
            // A field no longer in the catalog still shows, as a short text, so the chat history never breaks.
            AssessmentFields.Field definition = AssessmentFields.of(field.key()).orElseGet(() ->
                    new AssessmentFields.Field(field.key(), field.key().replace('_', ' '),
                            AssessmentFields.Input.TEXT, List.of(), null, 0, 200));
            return new AssessmentFieldView(field.key(), definition.label(), definition.input(),
                    definition.options().stream().map(o -> new AssessmentOptionView(o.key(), o.label())).toList(),
                    definition.unit(), definition.min(), definition.max(), field.value(), field.provId());
        }
    }

    @Schema(name = "ChatAssessmentOption", description = "One choice of an assessment question")
    record AssessmentOptionView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The value to answer with")
            String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label) {
    }

    @Schema(name = "ChatProposal", description = "The values a calculation will run with, shown before calculating")
    record ProposalView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OPEN: waiting for Calculate; "
                    + "CALCULATED: answered below; CANCELLED: a later question replaced it")
            ChatMessage.Proposal.Status status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProposalParameterView> parameters,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether Calculate answers with a "
                    + "range chart (a what-if) instead of a written answer") boolean range) {

        static ProposalView of(ChatMessage.Proposal proposal) {
            return new ProposalView(proposal.status(),
                    proposal.spec().parameters().stream().map(ProposalParameterView::of).toList(),
                    ToolPlan.Tool.ofFunction(proposal.spec().function())
                            .filter(t -> t == ToolPlan.Tool.SAVINGS_PROJECTION).isPresent());
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

    @Schema(name = "SaveAssessmentRequest", description = "Save my answers: one answer per question of the card")
    record SaveAssessmentRequest(
            @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED), maxItems = 40)
            @Size(max = 40) List<@Valid @NotNull Answer> answers) {

        SaveAssessmentRequest {
            // Missing answers are refused by the card check (which names the first question), after the id check.
            answers = answers == null ? List.of() : answers;
        }
    }

    @Schema(name = "AssessmentAnswer", description = "The answer to one question: a choice key, an amount or number "
            + "as a decimal string, or a short text")
    record Answer(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = ANSWER_KEY)
            @NotBlank @Pattern(regexp = ANSWER_KEY) String key,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
            @NotNull @Size(max = 200) String value) {
    }

    static final String ANSWER_KEY = "[a-z_]{1,64}";

    record PostMessageRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = PostMessageUseCase.MAX_LENGTH)
            @NotBlank @Size(max = PostMessageUseCase.MAX_LENGTH) String text) {
    }

    private final ShowChatUseCase showChat;
    private final PostMessageUseCase postMessage;
    private final AnswerMessageUseCase answerMessage;
    private final CalculateUseCase calculate;
    private final SaveAssessmentUseCase saveAssessment;

    ChatController(ShowChatUseCase showChat, PostMessageUseCase postMessage, AnswerMessageUseCase answerMessage,
            CalculateUseCase calculate, SaveAssessmentUseCase saveAssessment) {
        this.showChat = showChat;
        this.postMessage = postMessage;
        this.answerMessage = answerMessage;
        this.calculate = calculate;
        this.saveAssessment = saveAssessment;
    }

    @Operation(summary = "The conversation with every saved message")
    @GetMapping("/api/chat")
    ChatView chat(@Parameter(hidden = true) PersonaScope scope) {
        ShowChatUseCase.Chat chat = showChat.show(scope);
        return new ChatView(chat.personaName(), chat.messages().stream().map(MessageView::of).toList());
    }

    @Operation(summary = "The latest demands-and-needs statement Fine released in the chat",
            description = "Shown in \"Your insurance\" on Home (UX-DR44).")
    @ApiResponse(responseCode = "200", description = "The latest statement")
    @ApiResponse(responseCode = "204", description = "No statement yet")
    @GetMapping("/api/chat/needs-statement")
    ResponseEntity<NeedsStatementView> needsStatement(@Parameter(hidden = true) PersonaScope scope) {
        return showChat.needsStatement(scope).map(NeedsStatementView::of).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
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

    @Operation(summary = "Save my answers on an assessment card and return Fine's answer to the original question",
            description = "The answers are stored only by this call, through the profile confirm control. A card "
                    + "that was already saved returns that answer again.")
    @ApiResponse(responseCode = "200", description = "Fine's answer to the question the card was opened for")
    @ApiResponse(responseCode = "400", description = "An answer is missing, not on the card or does not fit")
    @ApiResponse(responseCode = "404", description = "No such assessment card for this persona")
    @ApiResponse(responseCode = "409", description = "The card was cancelled by a later question")
    @ApiResponse(responseCode = "503", description = "The answer could not be finished; nothing is stored and the "
            + "card stays open")
    @PostMapping("/api/chat/messages/{id}/assessment")
    MessageView saveAssessment(@Parameter(hidden = true) PersonaScope scope,
            @PathVariable @Parameter(description = "The card's id (a UUID)") String id,
            @Valid @RequestBody SaveAssessmentRequest request) {
        ErrorResponseException notFound = problem(HttpStatus.NOT_FOUND, "We couldn't find this message.", null);
        UUID messageId;
        try {
            messageId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound;
        }
        Map<String, String> answers = new LinkedHashMap<>();
        for (Answer answer : request.answers()) {
            if (answers.put(answer.key(), answer.value()) != null) {
                throw problem(HttpStatus.BAD_REQUEST, "Each question can be answered only once.",
                        ProblemCode.VALIDATION_FAILED);
            }
        }
        return saveAssessment.save(scope, messageId, answers).map(MessageView::of).orElseThrow(() -> notFound);
    }

    @ExceptionHandler(SaveAssessmentUseCase.AssessmentClosedException.class)
    ProblemDetail assessmentClosed() {
        return problem(HttpStatus.CONFLICT, "This form was replaced by your next question.",
                ProblemCode.ASSESSMENT_CLOSED).getBody();
    }

    @ExceptionHandler(SaveAssessmentUseCase.InvalidAnswersException.class)
    ProblemDetail invalidAnswers(SaveAssessmentUseCase.InvalidAnswersException ex) {
        return problem(HttpStatus.BAD_REQUEST, String.join(" ", ex.problems()), ProblemCode.VALIDATION_FAILED)
                .getBody();
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
