package de.financetool.core;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.WealthFigures;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Module API: the single entry point to the Deterministic Core (AD-2). Every call reads its data for the context's
 * persona and {@code asOf}, keys every output with a figure key and stores its provenance as an evidence item, in the
 * caller's transaction.
 */
public interface CoreGateway {

    /** The output of {@link #investmentTotals} that is the value of the persona's investments today. */
    String INVESTMENTS_TODAY = "holdingsValue";

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

    /**
     * The insurance figures at the end of {@code asOf}: each contract started by then with its keyed premium and next
     * due date (none if the persona does not pay it), the Duplicate Coverage check and the missing-essential-cover
     * check; each flag names the output whose key it carries.
     */
    InsuranceFigures insurance(RequestContext context, EvidenceOrigin origin);

    /**
     * The value of the persona's investments today and their monthly savings plan contributions (function
     * {@code investmentTotals}), with the same figure keys as in {@link #wealth}; empty without holdings and savings
     * plans.
     */
    Optional<CoreResult> investmentTotals(RequestContext context, EvidenceOrigin origin);

    /**
     * How many full month-end withdrawals a portfolio pays out under the effective and the monthly-rate convention
     * (function {@code withdrawalDuration}).
     *
     * @param portfolioFigure the Core figure the portfolio is, or {@code null} if the persona stated it
     * @param annualRate the assumed annual return as a fraction
     * @throws IllegalArgumentException if the withdrawal is not positive or the portfolio is negative
     */
    CoreResult withdrawalDuration(RequestContext context, EvidenceOrigin origin, BigDecimal portfolio,
            SourcedNumber portfolioFigure, BigDecimal monthlyWithdrawal, BigDecimal annualRate);
}
