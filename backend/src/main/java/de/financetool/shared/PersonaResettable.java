package de.financetool.shared;

import de.financetool.shared.domain.PersonaId;

/**
 * Implemented by every module that owns persona state (AD-12): persona reset calls each owner in reverse dependency
 * order inside one transaction. A module that adds persona-owned tables implements this and is listed in the reset
 * order (definition of done for those stories).
 */
public interface PersonaResettable {

    /** The owning module's name, as in the AD-12 module graph. */
    String module();

    /** Restores the persona's state in this module to its fixture; runs in the caller's transaction. */
    void reset(PersonaId personaId);
}
