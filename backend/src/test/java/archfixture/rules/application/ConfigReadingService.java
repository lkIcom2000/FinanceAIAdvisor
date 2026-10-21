package archfixture.rules.application;

import de.financetool.shared.config.AgentConfiguration;

/** Deliberately broken: a service that injects the configuration instead of receiving a RequestContext (AD-22). */
public class ConfigReadingService {

    private final AgentConfiguration agentConfig;

    public ConfigReadingService(AgentConfiguration agentConfig) {
        this.agentConfig = agentConfig;
    }

    public String model() {
        return agentConfig.llm().phrasing();
    }
}
