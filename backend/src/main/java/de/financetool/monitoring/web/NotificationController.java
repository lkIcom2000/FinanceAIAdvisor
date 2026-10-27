package de.financetool.monitoring.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.financetool.monitoring.application.ShowNotificationsUseCase;
import de.financetool.monitoring.domain.NotificationStatus;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.web.FigureView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * What Fine noticed (FR-7, FR-32), for the Home tab. The persona comes only from the session; another persona's
 * Notification answers exactly like an unknown one.
 */
@Tag(name = "notifications", description = "What Fine noticed about your finances")
@RestController
class NotificationController {

    /** Problems name the collection, never the requested id. */
    private static final URI INSTANCE = URI.create("/api/notifications");

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "A note from Fine's monitoring, with the figures that triggered it")
    record Notification(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) NotificationStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The plain explanation incl. outcome")
            String body,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The date Fine noticed it")
            LocalDate date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FigureView> figures,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean read,
            @Schema(description = "The prepared chat question for \"Ask Fine about this\"; absent if none")
            String question,
            @Schema(description = "The Action Fine prepared, for \"Review and confirm\"; absent if none")
            UUID actionId,
            @Schema(description = "Why the Action needs your OK, in plain words; absent if none") String reason) {
    }

    private final ShowNotificationsUseCase showNotifications;

    NotificationController(ShowNotificationsUseCase showNotifications) {
        this.showNotifications = showNotifications;
    }

    @Operation(summary = "Your Notifications, newest first")
    @GetMapping("/api/notifications")
    List<Notification> list(@Parameter(hidden = true) PersonaScope scope) {
        return showNotifications.list(scope).stream()
                .map(i -> new Notification(i.id(), i.status(), i.title(), i.body(), i.date(),
                        i.figures().stream().map(FigureView::of).toList(), i.read(), i.question(), i.actionId(),
                        i.reason()))
                .toList();
    }

    @Operation(summary = "Mark a Notification as read; marking it again changes nothing")
    @ApiResponse(responseCode = "204", description = "Read")
    @ApiResponse(responseCode = "404", description = "No such Notification for this persona")
    @PostMapping("/api/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@Parameter(hidden = true) PersonaScope scope, @PathVariable String id) {
        UUID notificationId;
        try {
            notificationId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
        if (!showNotifications.markRead(scope, notificationId)) {
            throw notFound();
        }
    }

    private static ErrorResponseException notFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "We couldn't find this notification.");
        problem.setInstance(INSTANCE);
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }
}
