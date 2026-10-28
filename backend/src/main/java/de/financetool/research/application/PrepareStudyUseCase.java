package de.financetool.research.application;

import de.financetool.actions.Rules;
import de.financetool.monitoring.Monitoring;
import de.financetool.personas.PersonaClock;
import de.financetool.personas.PersonaDirectory;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.web.PersonaScopeResolver;
import de.financetool.shared.web.SecurityConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prepares a persona for a study session from a {@link StudyPresets preset} (FR-31): reset to the fixtures, simulated
 * date, setup steps and a monitoring run, in one transaction, so every participant starts in the same state.
 */
@Service
@Profile(SecurityConfig.RESEARCH_PROFILE)
public class PrepareStudyUseCase {

    /** The status chips as the Home tab labels them, in the order the summary counts them. */
    static final Map<String, String> STATUS_LABELS = statusLabels();

    /** What the participant will see after the preparation. */
    public record Prepared(String presetId, String title, String personaId, LocalDate simulatedDate,
            List<Monitoring.RunNotification> notifications, int activeRules, String summary) {
    }

    private final StudyPresets presets;
    private final PersonaFixtureLoader loader;
    private final PersonaClock personas;
    private final RequestContextFactory contexts;
    private final Monitoring monitoring;
    private final Rules rules;
    private final PersonaDirectory directory;

    PrepareStudyUseCase(StudyPresets presets, PersonaFixtureLoader loader, PersonaClock personas,
            RequestContextFactory contexts, Monitoring monitoring, Rules rules, PersonaDirectory directory) {
        this.presets = presets;
        this.loader = loader;
        this.personas = personas;
        this.contexts = contexts;
        this.monitoring = monitoring;
        this.rules = rules;
        this.directory = directory;
    }

    /** @return empty if there is no preset with this id */
    @Transactional
    public Optional<Prepared> prepare(String presetId) {
        Optional<StudyPresets.Preset> found = presets.find(presetId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        StudyPresets.Preset preset = found.get();
        loader.load(preset.persona());
        personas.setSimulatedDate(preset.persona(), preset.simulatedDate());
        PersonaScope scope = PersonaScopeResolver.forResearcher(preset.persona());
        // Setup steps run at the simulated date, before monitoring, so a rule can cover the run's Actions.
        for (StudyPresets.MandateStep step : preset.mandates()) {
            this.rules.createRule(contexts.forPersona(scope, null), scope, directory.fixtureVersion(scope),
                    step.template(), step.bounds());
        }
        Monitoring.Run run = monitoring.run(contexts.forPersona(scope, null), scope);
        int rules = this.rules.activeRules(scope);
        return Optional.of(new Prepared(preset.id(), preset.title(), preset.personaId(), preset.simulatedDate(),
                run.notifications(), rules, summary(run.notifications(), rules)));
    }

    /** E.g. "2 notifications (1 Done for you, 1 Recommendation), 1 rule active". */
    static String summary(List<Monitoring.RunNotification> notifications, int activeRules) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        STATUS_LABELS.keySet().forEach(status -> counts.put(status, 0));
        notifications.forEach(n -> counts.merge(n.status(), 1, Integer::sum));
        List<String> parts = new ArrayList<>();
        counts.forEach((status, count) -> {
            if (count > 0) {
                parts.add(count + " " + STATUS_LABELS.getOrDefault(status, status));
            }
        });
        String text = plural(notifications.size(), "notification", "notifications");
        if (!parts.isEmpty()) {
            text += " (" + String.join(", ", parts) + ")";
        }
        return text + ", " + plural(activeRules, "rule", "rules") + " active";
    }

    private static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    private static Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("FOR_YOUR_INFORMATION", "For your information");
        labels.put("RECOMMENDATION", "Recommendation");
        labels.put("NEEDS_YOUR_OK", "Needs your OK");
        labels.put("DONE_FOR_YOU", "Done for you");
        labels.put("UNDONE", "Undone");
        labels.put("WITH_AN_ADVISOR", "With an advisor");
        return Collections.unmodifiableMap(labels);
    }
}
