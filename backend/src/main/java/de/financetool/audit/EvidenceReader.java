package de.financetool.audit;

import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.SourcedNumber;
import java.util.List;

/** Module API: reads stored figures back from the persona's own evidence items (AD-2); nothing is recomputed. */
public interface EvidenceReader {

    /**
     * The figures stored under {@code provIds}, in the given order; a key without an evidence item of the scope's
     * persona is left out. Runs in the caller's transaction.
     */
    List<SourcedNumber> figures(PersonaScope scope, List<String> provIds);
}
