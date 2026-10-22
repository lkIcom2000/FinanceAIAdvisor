package de.financetool.personas;

import de.financetool.shared.PersonaScope;

/** Module API: read the persona of a {@link PersonaScope}. */
public interface PersonaDirectory {

    PersonaSummary find(PersonaScope scope);

    /** The fixture version the persona's state was loaded from, for Audit Records. */
    String fixtureVersion(PersonaScope scope);
}
