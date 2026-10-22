package de.financetool.core.application;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContextFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Liquidity tab: balances, emergency fund and the cash-flow forecast; every figure is stored as evidence. */
@Service
public class ShowLiquidityOverviewUseCase {

    private final RequestContextFactory contexts;
    private final CoreGateway core;

    ShowLiquidityOverviewUseCase(RequestContextFactory contexts, CoreGateway core) {
        this.contexts = contexts;
        this.core = core;
    }

    @Transactional
    public LiquidityFigures show(PersonaScope scope) {
        return core.liquidity(contexts.forPersona(scope, null), EvidenceOrigin.OVERVIEW);
    }
}
