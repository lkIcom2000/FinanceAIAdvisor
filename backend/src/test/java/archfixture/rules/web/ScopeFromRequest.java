package archfixture.rules.web;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.PersonaId;

/** Deliberately broken: builds a persona scope from a client-supplied id (AD-11). */
public class ScopeFromRequest {

    public PersonaScope fromPath(String personaIdFromPath) {
        return new PersonaScope(new PersonaId(personaIdFromPath));
    }
}
