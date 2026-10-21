package de.financetool.research.application;

import de.financetool.shared.PersonaResettable;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stops startup in every profile when an owner of persona state is missing from the reset order, not only where the
 * reset endpoint exists (research profile).
 */
@Component
class ResetOrderCheck {

    ResetOrderCheck(List<PersonaResettable> owners) {
        ResetPersonaUseCase.requireRegistered(owners);
    }
}
