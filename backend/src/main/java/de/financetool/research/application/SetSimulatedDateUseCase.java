package de.financetool.research.application;

import de.financetool.personas.PersonaClock;
import de.financetool.shared.domain.PersonaId;
import de.financetool.shared.web.SecurityConfig;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sets a persona's simulated date (AD-8); the next {@code RequestContext.asOf} for that persona is this date. */
@Service
@Profile(SecurityConfig.RESEARCH_PROFILE)
public class SetSimulatedDateUseCase {

    private final PersonaClock personas;

    SetSimulatedDateUseCase(PersonaClock personas) {
        this.personas = personas;
    }

    /** @return {@code false} if the persona does not exist */
    @Transactional
    public boolean set(PersonaId personaId, LocalDate date) {
        if (!personas.exists(personaId)) {
            return false;
        }
        personas.setSimulatedDate(personaId, date);
        return true;
    }
}
