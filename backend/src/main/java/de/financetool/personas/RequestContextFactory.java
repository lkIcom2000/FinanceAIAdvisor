package de.financetool.personas;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;

/**
 * Module API: builds the {@link RequestContext} a use case runs with, from the active configuration and the persona's
 * simulated date (AD-8, AD-22). The only component that holds the configuration beans.
 */
public interface RequestContextFactory {

    /**
     * @param runId the acceptance-run id, or {@code null} outside acceptance runs
     */
    RequestContext forPersona(PersonaScope scope, String runId);
}
