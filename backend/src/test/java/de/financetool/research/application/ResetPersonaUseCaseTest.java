package de.financetool.research.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.personas.PersonaClock;
import de.financetool.shared.PersonaResettable;
import de.financetool.shared.domain.PersonaId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Owners run in RESET_ORDER regardless of registration order; an unlisted owner stops startup. */
class ResetPersonaUseCaseTest {

    static final PersonaId LENA = new PersonaId("p-lena");

    static PersonaResettable owner(String module, List<String> calls) {
        return new PersonaResettable() {
            @Override
            public String module() {
                return module;
            }

            @Override
            public void reset(PersonaId personaId) {
                calls.add(module + ":" + personaId);
            }
        };
    }

    @Test
    void ownersRunInReverseDependencyOrder() {
        List<String> calls = new ArrayList<>();
        PersonaClock personas = mock(PersonaClock.class);
        when(personas.exists(LENA)).thenReturn(true);
        ResetPersonaUseCase useCase = new ResetPersonaUseCase(personas,
                List.of(owner("personas", calls), owner("bank", calls), owner("monitoring", calls)));

        assertThat(useCase.reset(LENA)).isTrue();

        assertThat(calls).containsExactly("monitoring:p-lena", "bank:p-lena", "personas:p-lena");
    }

    @Test
    void anUnknownPersonaResetsNothing() {
        List<String> calls = new ArrayList<>();
        ResetPersonaUseCase useCase = new ResetPersonaUseCase(mock(PersonaClock.class),
                List.of(owner("personas", calls)));

        assertThat(useCase.reset(new PersonaId("p-nobody"))).isFalse();
        assertThat(calls).isEmpty();
    }

    @Test
    void theRegistrationCheckRunsInEveryProfile() {
        assertThat(ResetOrderCheck.class.isAnnotationPresent(org.springframework.context.annotation.Profile.class))
                .isFalse();
        assertThatThrownBy(() -> new ResetOrderCheck(List.of(owner("knowledge", new ArrayList<>()))))
                .hasMessageContaining("knowledge");
    }

    @Test
    void anOwnerOutsideTheResetOrderStopsStartup() {
        assertThatThrownBy(() -> new ResetPersonaUseCase(mock(PersonaClock.class),
                List.of(owner("knowledge", new ArrayList<>()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("knowledge")
                .hasMessageContaining("RESET_ORDER");
    }
}
