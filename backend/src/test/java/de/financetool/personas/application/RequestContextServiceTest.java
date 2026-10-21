package de.financetool.personas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.personas.adapter.PersonaStateEntity;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaScope;
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
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class RequestContextServiceTest {

    static final DerivedFrom PRD = new DerivedFrom("none", "none", LocalDate.of(2026, 10, 20), "PRD starting value");
    static final AgentConfiguration AGENT = new AgentConfiguration("reference", PRD,
            new AgentConfiguration.Llm("m1", "m2", "m3", "bge-m3"), true, "advisor", ComplianceLevel.A_B_C,
            DataExposureLevel.AGGREGATED, new AgentConfiguration.Decoding(0.0, 0.9, 42L, 4096, 700),
            new AgentConfiguration.Seeds(20261020L, 42L));
    static final AutonomyThresholds THRESHOLDS =
            new AutonomyThresholds(PRD, new BigDecimal("0.05"), new BigDecimal("0.95"), 5);
    static final ConfigFingerprint FINGERPRINT = new ConfigFingerprint("abc", new TreeMap<>());

    @Test
    void asOfIsThePersonasSimulatedDateAndEverythingElseComesFromTheActiveConfiguration() {
        PersonaStateEntity state = mock(PersonaStateEntity.class);
        when(state.getSimulatedDate()).thenReturn(LocalDate.of(2026, 11, 6));
        PersonaStateRepository repository = mock(PersonaStateRepository.class);
        when(repository.findByPersonaId("p-lena")).thenReturn(Optional.of(state));
        when(repository.find(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();

        RequestContext context = new RequestContextService(AGENT, FINGERPRINT, THRESHOLDS, repository)
                .forPersona(new PersonaScope(new PersonaId("p-lena")), null);

        assertThat(context.asOf()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(context.personaId()).isEqualTo(new PersonaId("p-lena"));
        assertThat(context.agentConfig()).isSameAs(AGENT);
        assertThat(context.configFingerprint()).isSameAs(FINGERPRINT);
        assertThat(context.thresholds()).isSameAs(THRESHOLDS);
        assertThat(context.seeds()).isEqualTo(new AgentConfiguration.Seeds(20261020L, 42L));
        assertThat(context.kbSnapshot()).isEqualTo(RequestContext.NO_KB_SNAPSHOT);
        assertThat(context.runId()).isNull();
    }
}
