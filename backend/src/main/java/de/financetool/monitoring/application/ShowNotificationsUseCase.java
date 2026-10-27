package de.financetool.monitoring.application;

import de.financetool.actions.ActionSummary;
import de.financetool.actions.Actions;
import de.financetool.actions.domain.ActionState;
import de.financetool.audit.EvidenceReader;
import de.financetool.monitoring.adapter.NotificationRepository;
import de.financetool.monitoring.domain.NotificationStatus;
import de.financetool.monitoring.domain.NotificationTemplates;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Home tab's Notifications (FR-7, FR-32): status from the linked Action's current state (AD-10), figures read back
 * from the persona's evidence items, and the read state.
 */
@Service
public class ShowNotificationsUseCase {

    /**
     * One Notification as the customer sees it.
     *
     * @param date the simulated date of the monitoring run
     * @param figures the cited figures in cited order; one without stored evidence is left out
     * @param question the prepared chat question, or {@code null}
     * @param actionId the linked Action, or {@code null}
     * @param reason why the linked Action needs the user's OK or was refused, or {@code null}
     */
    public record Item(UUID id, NotificationStatus status, String title, String body, LocalDate date,
            List<SourcedNumber> figures, boolean read, String question, UUID actionId, String reason) {

        public Item {
            figures = List.copyOf(figures);
        }
    }

    private final NotificationRepository notifications;
    private final Actions actions;
    private final EvidenceReader evidence;

    private final RequestContextFactory contexts;

    ShowNotificationsUseCase(NotificationRepository notifications, Actions actions, EvidenceReader evidence,
            RequestContextFactory contexts) {
        this.notifications = notifications;
        this.actions = actions;
        this.evidence = evidence;
        this.contexts = contexts;
    }

    /**
     * The persona's Notifications up to the simulated date, newest first: after the date moved back, later ones stay
     * hidden until it reaches them again.
     */
    @Transactional(readOnly = true)
    public List<Item> list(PersonaScope scope) {
        LocalDate asOf = contexts.forPersona(scope, null).asOf();
        List<NotificationRepository.Listed> listed = notifications.findNewestFirst(scope).stream()
                .filter(l -> !l.row().asOf().isAfter(asOf))
                .toList();
        Map<UUID, ActionSummary> summaries = actions.summaries(scope, listed.stream()
                .map(l -> l.row().actionId()).filter(Objects::nonNull).toList());
        // One evidence read for the whole list: the poll runs every 10 s.
        Map<String, SourcedNumber> figures = evidence.figures(scope, listed.stream()
                        .flatMap(l -> l.row().citedProvIds().stream()).distinct().toList())
                .stream().collect(Collectors.toMap(SourcedNumber::provId, f -> f));
        return listed.stream().map(l -> {
            NotificationRepository.Row row = l.row();
            ActionSummary action = row.actionId() == null ? null : summaries.get(row.actionId());
            // The stored body is the proposal; the outcome follows the Action's current state.
            String body = action != null && action.state() == ActionState.EXECUTED
                    ? row.body() + " " + NotificationTemplates.ACTION_DONE : row.body();
            return new Item(row.id(), NotificationStatus.of(row.outcome(), action == null ? null : action.state()),
                    row.title(), body, row.asOf(),
                    row.citedProvIds().stream().filter(figures::containsKey).map(figures::get).toList(), l.read(),
                    row.question(), row.actionId(), action == null ? null : action.reason());
        }).toList();
    }

    /** @return {@code false} if no Notification with this id belongs to the scope's persona */
    @Transactional
    public boolean markRead(PersonaScope scope, UUID id) {
        return notifications.markRead(scope, id);
    }
}
