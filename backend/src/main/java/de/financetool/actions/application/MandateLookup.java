package de.financetool.actions.application;

import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.shared.PersonaScope;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** The Mandate covering an Action, if any. */
interface MandateLookup {

    /**
     * The persona's non-revoked rule for the Action's type with the Action's accounts, with the amounts its executed
     * Actions moved in the calendar month of {@code asOf}.
     */
    Optional<MandateBounds> covering(PersonaScope scope, GateAction action, UUID sourceAccountId,
            UUID targetAccountId, LocalDate asOf);

    /** The number of the persona's Mandates that are not revoked. */
    int active(PersonaScope scope);
}
