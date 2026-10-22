package de.financetool.testing;

import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.AutonomyThresholds;
import de.financetool.shared.config.ComplianceLevel;
import de.financetool.shared.config.ConfigFingerprint;
import de.financetool.shared.config.DataExposureLevel;
import de.financetool.shared.config.DerivedFrom;
import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * The one shared fixture for hand-built configuration and {@link RequestContext}s in unit tests (Epic 1 retro A5).
 * Tests that need a variation derive it from these values instead of building their own.
 */
public final class TestContexts {

    public static final DerivedFrom PRD =
            new DerivedFrom("none", "none", LocalDate.of(2026, 10, 20), "PRD starting value");
    public static final AgentConfiguration.Decoding DECODING =
            new AgentConfiguration.Decoding(0.0, 0.9, 42L, 4096, 700);
    public static final AgentConfiguration.Seeds SEEDS = new AgentConfiguration.Seeds(20261020L, 42L);
    public static final AgentConfiguration AGENT = agent(new AgentConfiguration.Llm("m1", "m2", "m3", "bge-m3"));
    public static final AutonomyThresholds THRESHOLDS =
            new AutonomyThresholds(PRD, new BigDecimal("0.05"), new BigDecimal("0.95"), 5);
    public static final ConfigFingerprint FINGERPRINT =
            new ConfigFingerprint("f00d", new TreeMap<>(Map.of("config/b.yaml", "bb", "config/a.yaml", "aa")));
    public static final PersonaId LENA = new PersonaId("p-lena");
    public static final LocalDate AS_OF = LocalDate.of(2026, 10, 20);

    private TestContexts() {
    }

    /** The reference agent configuration with the given model tags. */
    public static AgentConfiguration agent(AgentConfiguration.Llm llm) {
        return new AgentConfiguration("reference", PRD, llm, true, "advisor", ComplianceLevel.A_B_C,
                DataExposureLevel.AGGREGATED, DECODING, SEEDS);
    }

    /** A context for p-lena at {@link #AS_OF}, outside acceptance runs. */
    public static RequestContext context() {
        return context(LENA, AS_OF);
    }

    public static RequestContext context(PersonaId personaId, LocalDate asOf) {
        return new RequestContext(AGENT, FINGERPRINT, THRESHOLDS, RequestContext.NO_KB_SNAPSHOT, SEEDS, asOf,
                personaId, null);
    }

    /** A context for p-lena at {@link #AS_OF} with other thresholds or seeds. */
    public static RequestContext context(AutonomyThresholds thresholds, AgentConfiguration.Seeds seeds) {
        return new RequestContext(AGENT, FINGERPRINT, thresholds, RequestContext.NO_KB_SNAPSHOT, seeds, AS_OF, LENA,
                null);
    }
}
