package de.financetool.personas;

import de.financetool.shared.PersonaScope;

/** Module API: read the persona of a {@link PersonaScope}. */
public interface PersonaDirectory {

    PersonaSummary find(PersonaScope scope);
}
