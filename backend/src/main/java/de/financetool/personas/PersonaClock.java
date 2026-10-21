package de.financetool.personas;

import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;

/**
 * Module API for the researcher's persona operations (AD-11 research endpoints): the persona id comes from the
 * request path there, so these methods take a {@link PersonaId}, not a {@code PersonaScope}.
 */
public interface PersonaClock {

    boolean exists(PersonaId personaId);

    /** Sets the persona's simulated date; runs in the caller's transaction. */
    void setSimulatedDate(PersonaId personaId, LocalDate date);
}
