package de.financetool.core;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.WealthFigures;
import de.financetool.shared.RequestContext;

/**
 * Module API: the single entry point to the Deterministic Core (AD-2). Every call reads its data for the context's
 * persona and {@code asOf}, keys every output with a figure key and stores its provenance as an evidence item, in the
 * caller's transaction.
 */
public interface CoreGateway {

    /** The persona's current account balance at the end of {@code asOf} (function {@code accountBalance}). */
    CoreResult currentAccountBalance(RequestContext context, EvidenceOrigin origin);

    /**
     * The liquidity figures at the end of {@code asOf}: account balances, liquid assets, variable spending, emergency
     * fund against its target, the three-month cash-flow forecast and its shortfall check. Inputs that are figures
     * themselves carry their own keys (drill-down).
     *
     * @throws IllegalStateException if a Rule Parameter has no version valid at {@code asOf}
     */
    LiquidityFigures liquidity(RequestContext context, EvidenceOrigin origin);

    /**
     * The wealth figures at the end of {@code asOf}: each holding's value, each active savings plan's monthly amount
     * and, if there is at least one of them, the seeded goal projection (seed {@code context.seeds().core()}) with its
     * starting value and monthly contribution.
     *
     * @throws IllegalStateException if a Rule Parameter has no version valid at {@code asOf}
     */
    WealthFigures wealth(RequestContext context, EvidenceOrigin origin);
}
