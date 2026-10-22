package de.financetool.shared;

/**
 * Builds the {@link RequestContext} a use case runs with, from the active configuration and the persona's simulated
 * date (AD-8, AD-22). Declared in {@code shared} so that every module's use cases can build a context without
 * depending on {@code personas}, which implements it ({@code personas.application.RequestContextService}, the only
 * component that holds the configuration beans).
 */
public interface RequestContextFactory {

    /**
     * @param runId the acceptance-run id, or {@code null} outside acceptance runs
     */
    RequestContext forPersona(PersonaScope scope, String runId);
}
