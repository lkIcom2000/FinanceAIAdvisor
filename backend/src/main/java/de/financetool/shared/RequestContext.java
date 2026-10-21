package de.financetool.shared;

import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.AutonomyThresholds;
import de.financetool.shared.config.ConfigFingerprint;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Everything a use case runs with (AD-22): built once per use case from the active configuration and the persona's
 * state, then passed explicitly to every service. Services unpack it into plain arguments for domain functions.
 *
 * @param kbSnapshot the Knowledge Base snapshot id; {@link #NO_KB_SNAPSHOT} until the Knowledge Base exists
 * @param asOf the persona's simulated date: the only "today" in domain logic (AD-8)
 * @param runId the acceptance-run id, or {@code null} outside acceptance runs
 */
public record RequestContext(
        AgentConfiguration agentConfig,
        ConfigFingerprint configFingerprint,
        AutonomyThresholds thresholds,
        String kbSnapshot,
        AgentConfiguration.Seeds seeds,
        LocalDate asOf,
        PersonaId personaId,
        String runId) {

    public static final String NO_KB_SNAPSHOT = "none";

    public RequestContext {
        Objects.requireNonNull(agentConfig, "agentConfig");
        Objects.requireNonNull(configFingerprint, "configFingerprint");
        Objects.requireNonNull(thresholds, "thresholds");
        Objects.requireNonNull(kbSnapshot, "kbSnapshot");
        Objects.requireNonNull(seeds, "seeds");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(personaId, "personaId");
    }
}
