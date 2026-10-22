package de.financetool.audit.application;

import de.financetool.audit.Provenance;
import de.financetool.shared.PersonaScope;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The provenance of one figure (FR-8), read only from the persona's own evidence items. */
@Service
public class ShowProvenanceUseCase {

    private final AuditService audit;

    ShowProvenanceUseCase(AuditService audit) {
        this.audit = audit;
    }

    /** @return empty if no evidence item with this key belongs to the scope's persona */
    @Transactional(readOnly = true)
    public Optional<Provenance> show(PersonaScope scope, String provId) {
        return audit.provenance(scope, provId);
    }
}
