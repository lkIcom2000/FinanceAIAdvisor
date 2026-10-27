package de.financetool.actions.application;

import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.shared.PersonaScope;
import java.util.Optional;

/** The Mandate covering an Action, if any. Epic 9 adds Mandates; until then none covers anything. */
interface MandateLookup {

    Optional<MandateBounds> covering(PersonaScope scope, GateAction action);

    /** The number of the persona's Mandates that are not revoked. */
    int active(PersonaScope scope);
}
