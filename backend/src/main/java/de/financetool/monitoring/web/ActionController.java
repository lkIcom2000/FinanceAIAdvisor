package de.financetool.monitoring.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.financetool.actions.ActionConfirmation;
import de.financetool.actions.ActionReview;
import de.financetool.actions.ActionUndo;
import de.financetool.actions.NotAwaitingConfirmationException;
import de.financetool.actions.NotUndoableException;
import de.financetool.monitoring.application.ConfirmActionUseCase;
import de.financetool.monitoring.domain.NotificationTemplates;
import de.financetool.shared.PersonaScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Review and confirm" on a Notification (FR-39, FR-40): what exactly would happen and why it needs the OK, and the
 * confirmation itself. The persona comes only from the session; another persona's Action answers exactly like an
 * unknown one.
 */
@Tag(name = "actions", description = "Confirm or undo a transfer")
@RestController
class ActionController {

    /** Problems name the collection, never the requested id. */
    private static final URI INSTANCE = URI.create("/api/actions");

    @Schema(description = "One account of the transfer, before and after it; balances in EUR as decimal strings")
    record AccountChange(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2700.00") String before,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2500.00") String after) {

        static AccountChange of(String name, BigDecimal before, BigDecimal after) {
            return new AccountChange(name, cents(before), cents(after));
        }

        private static String cents(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }

    @Schema(description = "The exact transfer waiting for your OK")
    record TransferReview(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Move 200.00 EUR from Overnight deposit to Current account") String sentence,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "200.00 EUR") String amountText,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AccountChange from,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AccountChange to,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Why it needs your OK") String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The \"Why?\" explanation") String why) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "The outcome of a confirmation")
    record ConfirmResult(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean done,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "200.00 EUR") String amountText,
            @Schema(description = "Why the transfer was not made; absent once done") String reason,
            @Schema(description = "The \"Why?\" explanation of the reason; absent once done") String why) {
    }

    private final ConfirmActionUseCase confirmAction;

    ActionController(ConfirmActionUseCase confirmAction) {
        this.confirmAction = confirmAction;
    }

    @Operation(summary = "The transfer waiting for your OK, with both accounts before and after")
    @ApiResponse(responseCode = "200", description = "The transfer")
    @ApiResponse(responseCode = "404", description = "No such Action for this persona")
    @ApiResponse(responseCode = "409", description = "Not a transfer waiting for your OK")
    @GetMapping("/api/actions/{id}/review")
    TransferReview review(@Parameter(hidden = true) PersonaScope scope, @PathVariable String id) {
        ActionReview r = confirmAction.review(scope, actionId(id))
                .orElseThrow(ActionController::notFound);
        String amount = NotificationTemplates.eur(r.amount());
        return new TransferReview("Move " + amount + " from " + r.fromName() + " to " + r.toName(), amount,
                AccountChange.of(r.fromName(), r.fromBefore(), r.fromAfter()),
                AccountChange.of(r.toName(), r.toBefore(), r.toAfter()), r.reason(), r.why());
    }

    @Operation(summary = "Confirm the transfer: Fine checks it again and makes it, or tells you why not")
    @ApiResponse(responseCode = "200", description = "Done, or refused with the reason")
    @ApiResponse(responseCode = "404", description = "No such Action for this persona")
    @ApiResponse(responseCode = "409", description = "Not (or no longer) a transfer waiting for your OK")
    @PostMapping("/api/actions/{id}/confirm")
    ConfirmResult confirm(@Parameter(hidden = true) PersonaScope scope, @PathVariable String id) {
        ActionConfirmation o = confirmAction.confirm(scope, actionId(id)).orElseThrow(ActionController::notFound);
        return new ConfirmResult(o.done(), NotificationTemplates.eur(o.amount()), o.reason(), o.why());
    }

    @Schema(description = "The outcome of an undo")
    record UndoResult(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Transfer undone. 90.00 EUR is back in your overnight deposit.") String message) {
    }

    @Operation(summary = "Undo a transfer Fine made within your rule, until its deadline")
    @ApiResponse(responseCode = "200", description = "Undone")
    @ApiResponse(responseCode = "404", description = "No such Action for this persona")
    @ApiResponse(responseCode = "409", description = "Not a rule transfer, already undone, or too late")
    @PostMapping("/api/actions/{id}/undo")
    UndoResult undo(@Parameter(hidden = true) PersonaScope scope, @PathVariable String id) {
        ActionUndo u = confirmAction.undo(scope, actionId(id)).orElseThrow(ActionController::notFound);
        return new UndoResult(NotificationTemplates.undone(u.amount(), u.backInOvernight()));
    }

    @ExceptionHandler(NotUndoableException.class)
    ProblemDetail notUndoable(NotUndoableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setInstance(INSTANCE);
        return problem;
    }

    @ExceptionHandler(NotAwaitingConfirmationException.class)
    ProblemDetail notAwaiting() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This transfer is no longer waiting for your OK.");
        problem.setInstance(INSTANCE);
        return problem;
    }

    private static UUID actionId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
    }

    private static ErrorResponseException notFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "We couldn't find this transfer.");
        problem.setInstance(INSTANCE);
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }
}
