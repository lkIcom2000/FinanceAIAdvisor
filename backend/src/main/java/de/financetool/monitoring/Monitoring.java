package de.financetool.monitoring;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import java.util.List;
import java.util.UUID;

/**
 * Module API: one monitoring run at the context's as-of date (FR-30, FR-31, AD-16). Never scheduled: research
 * endpoints, study preparation and acceptance suites start it.
 */
public interface Monitoring {

    /** A Trigger stored by the run. */
    record RunTrigger(UUID triggerId, String type, String subjectId, String period) {
    }

    /** An Action the run proposed, as the Autonomy Gate decided it; {@code level} is {@code null} if escalated. */
    record RunAction(UUID actionId, UUID triggerId, String type, String state, String level,
            List<String> cappingCriteria) {

        public RunAction {
            cappingCriteria = List.copyOf(cappingCriteria);
        }
    }

    /** What a run added: only new Triggers and their outputs. */
    record Run(List<RunTrigger> triggers, List<RunAction> actions, List<UUID> notificationIds) {

        public Run {
            triggers = List.copyOf(triggers);
            actions = List.copyOf(actions);
            notificationIds = List.copyOf(notificationIds);
        }
    }

    /**
     * Detects the Triggers at {@code context.asOf()} from Core figures only and, for each Trigger key not yet stored,
     * stores the Trigger, proposes its Action, opens its case and writes its Notifications, each with a
     * {@code NOTIFICATION} Audit Record, in the caller's transaction. A re-run at the same date adds nothing.
     *
     * @throws IllegalArgumentException if {@code scope} is not the context's persona
     */
    Run run(RequestContext context, PersonaScope scope);
}
