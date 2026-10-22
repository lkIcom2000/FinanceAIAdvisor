package de.financetool.core.application;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.WealthFigures;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Wealth &amp; Retirement tab: holdings, savings plans and the goal projection; every figure is stored as
 * evidence.
 */
@Service
public class ShowWealthOverviewUseCase {

    private final RequestContextFactory contexts;
    private final CoreGateway core;

    ShowWealthOverviewUseCase(RequestContextFactory contexts, CoreGateway core) {
        this.contexts = contexts;
        this.core = core;
    }

    @Transactional
    public WealthFigures show(PersonaScope scope) {
        return core.wealth(contexts.forPersona(scope, null), EvidenceOrigin.OVERVIEW);
    }
}
