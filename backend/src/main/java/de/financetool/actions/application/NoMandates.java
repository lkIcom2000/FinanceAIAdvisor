package de.financetool.actions.application;

import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.shared.PersonaScope;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The Epic 9 seam: no Mandates exist yet, so no Action is covered and none reaches L3. */
@Component
class NoMandates implements MandateLookup {

    @Override
    public Optional<MandateBounds> covering(PersonaScope scope, GateAction action) {
        return Optional.empty();
    }
}
