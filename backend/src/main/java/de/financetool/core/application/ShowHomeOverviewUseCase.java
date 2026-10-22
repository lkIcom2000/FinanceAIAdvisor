package de.financetool.core.application;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.AccountBalance;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.RequestContextFactory;
import de.financetool.shared.domain.SourcedNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Home tab's summary figures; each is stored as evidence so that "Why?" can explain it. */
@Service
public class ShowHomeOverviewUseCase {

    public record HomeOverview(SourcedNumber currentAccount) {
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
        return new HomeOverview(core.currentAccountBalance(context, EvidenceOrigin.OVERVIEW)
                .number(AccountBalance.OUTPUT));
    }
}
