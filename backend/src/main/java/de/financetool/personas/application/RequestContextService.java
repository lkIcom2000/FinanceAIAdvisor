package de.financetool.personas.application;

import de.financetool.personas.RequestContextFactory;
import de.financetool.personas.adapter.PersonaStateEntity;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.AutonomyThresholds;
import de.financetool.shared.config.ConfigFingerprint;
import org.springframework.stereotype.Service;

@Service
class RequestContextService implements RequestContextFactory {

    private final AgentConfiguration agentConfig;
    private final ConfigFingerprint configFingerprint;
    private final AutonomyThresholds thresholds;
    private final PersonaStateRepository personaStates;

    RequestContextService(AgentConfiguration agentConfig, ConfigFingerprint configFingerprint,
            AutonomyThresholds thresholds, PersonaStateRepository personaStates) {
        this.agentConfig = agentConfig;
        this.configFingerprint = configFingerprint;
        this.thresholds = thresholds;
        this.personaStates = personaStates;
    }

    @Override
    public RequestContext forPersona(PersonaScope scope, String runId) {
        PersonaStateEntity state = personaStates.find(scope)
                .orElseThrow(() -> new IllegalStateException("No persona state for " + scope.id()));
        return new RequestContext(agentConfig, configFingerprint, thresholds, RequestContext.NO_KB_SNAPSHOT,
                agentConfig.seeds(), state.getSimulatedDate(), scope.personaId(), runId);
    }
}
