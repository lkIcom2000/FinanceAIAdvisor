package de.financetool.actions.application;

import de.financetool.actions.adapter.MandateRepository;
import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.shared.PersonaScope;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The Mandate lookup over the stored rules. Story 9.2 lets a rule cover its Action; until then none does. */
@Component
class StoredMandates implements MandateLookup {

    private final MandateRepository mandates;

    StoredMandates(MandateRepository mandates) {
        this.mandates = mandates;
    }

    @Override
    public Optional<MandateBounds> covering(PersonaScope scope, GateAction action) {
        return Optional.empty();
    }

    @Override
    public int active(PersonaScope scope) {
        return mandates.countActive(scope);
    }
}
