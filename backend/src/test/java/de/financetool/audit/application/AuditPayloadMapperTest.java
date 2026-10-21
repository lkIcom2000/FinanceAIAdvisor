package de.financetool.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;

/** The payload format is fixed (AD-13): sorted keys, ISO dates, nulls kept, independent of field order. */
class AuditPayloadMapperTest {

    static final DerivedFrom PRD = new DerivedFrom("none", "none", LocalDate.of(2026, 10, 20), "PRD starting value");
    static final RequestContext CONTEXT = new RequestContext(
            new AgentConfiguration("reference", PRD, new AgentConfiguration.Llm("m1", "m2", "m3", "bge-m3"), true,
                    "advisor", ComplianceLevel.A_B_C, DataExposureLevel.AGGREGATED,
                    new AgentConfiguration.Decoding(0.0, 0.9, 42L, 4096, 700),
                    new AgentConfiguration.Seeds(20261020L, 42L)),
            new ConfigFingerprint("f00d", new TreeMap<>(Map.of("config/b.yaml", "bb", "config/a.yaml", "aa"))),
            new AutonomyThresholds(PRD, new BigDecimal("0.05"), new BigDecimal("0.95"), 5),
            RequestContext.NO_KB_SNAPSHOT, new AgentConfiguration.Seeds(20261020L, 42L),
            LocalDate.of(2026, 10, 20), new PersonaId("p-lena"), null);

    record Body(String zeta, BigDecimal amount, String alpha) {
    }

    final AuditPayloadMapper mapper = new AuditPayloadMapper();

    @Test
    void thePayloadHasAFixedSortedFormat() {
        assertThat(mapper.payload(CONTEXT, new Body("z", new BigDecimal("12.50"), null))).isEqualTo(
                "{\"asOf\":\"2026-10-20\","
                        + "\"body\":{\"alpha\":null,\"amount\":12.50,\"zeta\":\"z\"},"
                        + "\"config\":{\"files\":{\"config/a.yaml\":\"aa\",\"config/b.yaml\":\"bb\"},"
                        + "\"fingerprint\":\"f00d\",\"id\":\"reference\"},"
                        + "\"kbSnapshot\":\"none\","
                        + "\"seeds\":{\"core\":20261020,\"llm\":42}}");
    }

    @Test
    void storedJsonIsReSerialisedInTheSameFormat() {
        String json = mapper.payload(CONTEXT, Map.of("b", 1, "a", new BigDecimal("12.50")));
        assertThat(mapper.toJson(mapper.fromJson(json))).isEqualTo(json);
    }
}
