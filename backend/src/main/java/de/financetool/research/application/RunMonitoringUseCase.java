package de.financetool.research.application;

import de.financetool.monitoring.Monitoring;
import de.financetool.personas.PersonaClock;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.web.PersonaScopeResolver;
import de.financetool.shared.web.SecurityConfig;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs monitoring for a persona at its simulated date (FR-30, AD-16), in one transaction: a failure anywhere leaves no
 * Trigger, Action, Notification or Audit Record. There is no scheduler; this is the researcher's way to start a run.
 */
@Service
@Profile(SecurityConfig.RESEARCH_PROFILE)
public class RunMonitoringUseCase {

    private final PersonaClock personas;
    private final RequestContextFactory contexts;
    private final Monitoring monitoring;

    RunMonitoringUseCase(PersonaClock personas, RequestContextFactory contexts, Monitoring monitoring) {
        this.personas = personas;
        this.contexts = contexts;
        this.monitoring = monitoring;
    }

    /** @return empty if the persona does not exist */
    @Transactional
    public Optional<Monitoring.Run> run(PersonaId personaId) {
        if (!personas.exists(personaId)) {
            return Optional.empty();
        }
        PersonaScope scope = PersonaScopeResolver.forResearcher(personaId);
        return Optional.of(monitoring.run(contexts.forPersona(scope, null), scope));
    }
}
