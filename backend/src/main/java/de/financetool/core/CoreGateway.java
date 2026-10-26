package de.financetool.core;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.NeedsFigures;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.core.domain.RecommendationFigures;
import de.financetool.core.domain.WealthFigures;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Module API: the single entry point to the Deterministic Core (AD-2). Every call reads its data for the context's
 * persona and {@code asOf}, keys every output with a figure key and stores its provenance as an evidence item, in the
 * caller's transaction.
 */
public interface CoreGateway {

    /** The output of {@link #investmentTotals} that is the value of the persona's investments today. */
    String INVESTMENTS_TODAY = "holdingsValue";

    /** The output of {@link ProfileFigures#netIncome}: the average monthly net income. */
    String NET_INCOME = "monthly";
    /** The output of {@link ProfileFigures#emergencyFund} that is the emergency fund today. */
    String EMERGENCY_FUND = "current";
    /** The output of {@link ProfileFigures#emergencyFund} that is the average monthly expenses. */
    String MONTHLY_EXPENSES = "monthlyExpenses";

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
     * What the assessment card pre-fills from account data at the end of {@code asOf}: the monthly net income
     * (function {@code monthlyNetIncome}) and the emergency fund with the monthly expenses (function
     * {@code emergencyFundTarget}, same keys as in {@link #liquidity}).
     *
     * @throws IllegalStateException if a Rule Parameter has no version valid at {@code asOf}
     */
    ProfileFigures profileFigures(RequestContext context, EvidenceOrigin origin);

    /**
     * The Category Recommendation at {@code asOf} (FR-20, FR-27): the rule-based category selection (function
     * {@code categorySelection}) on the profile and Rule Parameters only, and for an investment topic the ex-ante cost
     * calculation (function {@code costCalculation}) on the profile's amount, else its requested monthly amount times
     * 12, else {@link de.financetool.core.domain.CostCalculation#DEFAULT_AMOUNT}, over
     * {@link de.financetool.core.domain.CostCalculation#YEARS} years.
     *
     * @param profile the effective profile, field → value (stored plus the facts the message states)
     * @param withCosts whether the topic is an investment topic
     * @throws de.financetool.knowledge.NoValidRuleParameterException if a Rule Parameter has no version valid at
     *     {@code asOf}; nothing is stored
     */
    RecommendationFigures recommendation(RequestContext context, EvidenceOrigin origin, Map<String, ?> profile,
            String topic, boolean withCosts);

    /**
     * The demands-and-needs statement at {@code asOf} (FR-18, function {@code demandsAndNeeds}): liability,
     * occupational disability and Duplicate Coverage from the Needs Profile and the contracts started by then.
     *
     * @param needsProfile the effective profile, field → value; only household, dependants and occupation are read
     */
    NeedsFigures needs(RequestContext context, EvidenceOrigin origin, Map<String, ?> needsProfile);

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

    /**
     * The Riester allowances at the as-of date for the income and children given (Core function {@code
     * riesterSubsidy}).
     *
     * @throws de.financetool.knowledge.NoValidRuleParameterException if a subsidy rule has no version valid at the
     *     as-of date; nothing is computed or stored
     */
    CoreResult riesterSubsidy(RequestContext context, EvidenceOrigin origin, BigDecimal priorYearGrossIncome,
            List<Integer> childrenBirthYears);

    /**
     * The Core's refusal without inputs: checks that every subsidy rule has a version valid on {@code date}.
     *
     * @throws de.financetool.knowledge.NoValidRuleParameterException if one has none
     */
    void requireSubsidyRules(LocalDate date);
}
