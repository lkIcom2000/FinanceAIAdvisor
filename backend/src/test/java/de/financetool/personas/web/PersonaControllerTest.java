package de.financetool.personas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.financetool.personas.PersonaSummary;
import de.financetool.personas.application.ShowPersonaUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** The simulated date reaches the UI only under the research profile (Story 1.4, UX-DR2). */
class PersonaControllerTest {

    private final PersonaScope lena = new PersonaScope(new PersonaId("p-lena"));
    private final ShowPersonaUseCase useCase = mock(ShowPersonaUseCase.class);

    PersonaControllerTest() {
        when(useCase.show(lena)).thenReturn(new ShowPersonaUseCase.PersonaView(
                new PersonaSummary("p-lena", "Lena"), LocalDate.of(2026, 10, 20)));
    }

    @Test
    void researchProfileExposesTheSimulatedDate() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("research");

        assertThat(new PersonaController(useCase, environment).current(lena).today())
                .isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void withoutTheResearchProfileTheDateIsAbsent() {
        assertThat(new PersonaController(useCase, new MockEnvironment()).current(lena).today()).isNull();
    }
}
