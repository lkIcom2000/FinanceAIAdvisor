package de.financetool.personas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static de.financetool.testing.TestContexts.AGENT;
import static de.financetool.testing.TestContexts.FINGERPRINT;
import static de.financetool.testing.TestContexts.SEEDS;
import static de.financetool.testing.TestContexts.THRESHOLDS;

import de.financetool.personas.adapter.PersonaStateEntity;
import de.financetool.personas.adapter.PersonaStateRepository;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RequestContextServiceTest {

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
        assertThat(context.seeds()).isEqualTo(SEEDS);
        assertThat(context.kbSnapshot()).isEqualTo(RequestContext.NO_KB_SNAPSHOT);
        assertThat(context.runId()).isNull();
    }
}
