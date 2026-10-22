package de.financetool.core.application;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Home tab: the summary (the Liquidity tab's figures, through the same gateway call, so they carry the same keys)
 * and "Your insurance"; every figure is stored as evidence so that "Why?" can explain it.
 */
@Service
public class ShowHomeOverviewUseCase {

    public record HomeOverview(LiquidityFigures liquidity, InsuranceFigures insurance) {
    }

    private final RequestContextFactory contexts;
    private final CoreGateway core;

    ShowHomeOverviewUseCase(RequestContextFactory contexts, CoreGateway core) {
        this.contexts = contexts;
        this.core = core;
    }

    @Transactional
    public HomeOverview show(PersonaScope scope) {
        RequestContext context = contexts.forPersona(scope, null);
        return new HomeOverview(core.liquidity(context, EvidenceOrigin.OVERVIEW),
                core.insurance(context, EvidenceOrigin.OVERVIEW));
    }
}
