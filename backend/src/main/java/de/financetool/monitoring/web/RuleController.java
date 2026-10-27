package de.financetool.monitoring.web;

import de.financetool.actions.InvalidRuleException;
import de.financetool.actions.Rule;
import de.financetool.actions.RuleAlreadyActiveException;
import de.financetool.actions.RuleAlreadyStoppedException;
import de.financetool.actions.RuleCheck;
import de.financetool.actions.RuleSetup;
import de.financetool.actions.domain.RuleBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.actions.domain.UndoWindow;
import de.financetool.monitoring.application.RulesUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.web.FigureView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
 * "Your rules" and the rule setup dialog (FR-36, FR-7, UX-DR22, UX-DR23). The persona comes only from the session.
 * Customer text says "rule", never "Mandate"; chat never calls these endpoints.
 */
@Tag(name = "rules", description = "Rules that let Fine move money within limits you set")
@RestController
class RuleController {

    private static final URI INSTANCE = URI.create("/api/rules");

    /** Longer amounts are refused before parsing; the limit checks cap amounts at 1,000,000 EUR anyway. */
    static final int MAX_AMOUNT_LENGTH = 16;

    @Schema(description = "A rule's limits; EUR as decimal strings")
    record Limits(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "300.00") String maxPerAction,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "600.00") String maxPerMonth,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2000.00",
                    description = "The source account's minimum balance after a transfer") String floor) {

        static Limits of(RuleBounds bounds) {
            return new Limits(cents(bounds.maxPerAction()), cents(bounds.maxPerMonth()), cents(bounds.floor()));
        }
    }

    @Schema(description = "A rule, active or stopped")
    record RuleView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RuleTemplate template,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The plain-language summary")
            String summary,
            @Schema(description = "The account money comes from; absent if it no longer exists") String fromName,
            @Schema(description = "The account money goes to; absent if it no longer exists") String toName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Limits limits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate activeSince,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int actionCount,
            @Schema(description = "The date the rule was stopped; absent while it is active") LocalDate stoppedOn,
            @Schema(description = "Until when the rule's last transfer can be undone; absent if it can't",
                    example = "Fri 16 Oct, 5 pm") String undoUntil) {

        static RuleView of(Rule rule) {
            return new RuleView(rule.id().toString(), rule.template(), rule.summary(), rule.fromName(),
                    rule.toName(), Limits.of(rule.bounds()), rule.activeSince(), rule.actionCount(),
                    rule.stoppedOn(), rule.undoUntil() == null ? null : UndoWindow.text(rule.undoUntil()));
        }
    }

    @Schema(description = "A rule template with the limits Fine suggests")
    record TemplateView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) RuleTemplate template,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fromName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String toName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean alreadyActive,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView maxPerAction,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView maxPerMonth,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView floor,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Where the suggestion comes from")
            String source) {

        static TemplateView of(RuleSetup s) {
            return new TemplateView(s.template(), s.title(), s.fromName(), s.toName(), s.alreadyActive(),
                    FigureView.of(s.maxPerAction()), FigureView.of(s.maxPerMonth()), FigureView.of(s.floor()),
                    s.source());
        }
    }

    @Schema(description = "The rule a persona asks for")
    record RuleRequest(RuleTemplate template, String maxPerAction, String maxPerMonth, String floor) {
    }

    @Schema(description = "The \"Check and confirm\" step")
    record CheckView(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> will,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> wont,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String undoAndStop) {
    }

    private final RulesUseCase rules;

    RuleController(RulesUseCase rules) {
        this.rules = rules;
    }

    @Operation(summary = "Your active rules, oldest first, then your stopped rules, latest first")
    @GetMapping("/api/rules")
    List<RuleView> rules(@Parameter(hidden = true) PersonaScope scope) {
        return rules.rules(scope).stream().map(RuleView::of).toList();
    }

    @Operation(summary = "The rule templates with the limits Fine suggests; empty without both accounts")
    @GetMapping("/api/rules/setup")
    List<TemplateView> setup(@Parameter(hidden = true) PersonaScope scope) {
        return rules.setup(scope).stream().map(TemplateView::of).toList();
    }

    @Operation(summary = "Check a rule's limits and describe it; nothing is stored")
    @ApiResponse(responseCode = "200", description = "The texts of the check step")
    @ApiResponse(responseCode = "400", description = "The limits can't be used")
    @PostMapping("/api/rules/check")
    CheckView check(@Parameter(hidden = true) PersonaScope scope, @RequestBody(required = false) RuleRequest request) {
        RuleCheck c = rules.check(template(request), bounds(request));
        return new CheckView(c.summary(), c.will(), c.wont(), c.undoAndStop());
    }

    @Operation(summary = "Start a rule")
    @ApiResponse(responseCode = "201", description = "The rule is active")
    @ApiResponse(responseCode = "400", description = "The limits can't be used")
    @ApiResponse(responseCode = "409", description = "A rule of this kind is already active")
    @PostMapping("/api/rules")
    @ResponseStatus(HttpStatus.CREATED)
    RuleView start(@Parameter(hidden = true) PersonaScope scope, @RequestBody(required = false) RuleRequest request) {
        return RuleView.of(rules.start(scope, template(request), bounds(request)));
    }

    @Operation(summary = "Stop a rule; transfers already made stay as they are")
    @ApiResponse(responseCode = "200", description = "The rule is stopped")
    @ApiResponse(responseCode = "404", description = "No such rule for this persona")
    @ApiResponse(responseCode = "409", description = "The rule is already stopped")
    @PostMapping("/api/rules/{id}/revoke")
    RuleView revoke(@Parameter(hidden = true) PersonaScope scope, @PathVariable String id) {
        return RuleView.of(rules.revoke(scope, ruleId(id)).orElseThrow(RuleController::notFound));
    }

    @ExceptionHandler(RuleAlreadyStoppedException.class)
    ProblemDetail alreadyStopped() {
        return problem(HttpStatus.CONFLICT, "This rule has already been stopped.");
    }

    @ExceptionHandler(InvalidRuleException.class)
    ProblemDetail invalid(InvalidRuleException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RuleAlreadyActiveException.class)
    ProblemDetail alreadyActive() {
        return problem(HttpStatus.CONFLICT, "You already have a rule like this. Stop it first to set up a new one.");
    }

    private static RuleTemplate template(RuleRequest request) {
        if (request == null || request.template() == null) {
            throw new InvalidRuleException("Please choose one of the two rules.");
        }
        return request.template();
    }

    private static RuleBounds bounds(RuleRequest request) {
        return new RuleBounds(amount(request.maxPerAction()), amount(request.maxPerMonth()), amount(request.floor()));
    }

    private static BigDecimal amount(String value) {
        if (value == null || value.length() > MAX_AMOUNT_LENGTH || !value.matches(FigureView.DECIMAL)) {
            throw new InvalidRuleException("Please enter each limit as an amount in EUR.");
        }
        return new BigDecimal(value);
    }

    private static String cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static UUID ruleId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
    }

    private static ErrorResponseException notFound() {
        return new ErrorResponseException(HttpStatus.NOT_FOUND,
                problem(HttpStatus.NOT_FOUND, "We couldn't find this rule."), null);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(INSTANCE);
        return problem;
    }
}
