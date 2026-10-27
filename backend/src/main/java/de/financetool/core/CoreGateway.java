package de.financetool.core;

import de.financetool.audit.EvidenceOrigin;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.domain.AccountIds;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.GateMeasures;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.NeedsFigures;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.core.domain.RecommendationFigures;
import de.financetool.core.domain.RuleChangeFigures;
import de.financetool.core.domain.TransferBalances;
import de.financetool.core.domain.WealthFigures;
import de.financetool.core.domain.WhatIfFigures;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    /** The output of {@link LiquidityFigures#emergencyFund} that is the emergency fund target. */
    String EMERGENCY_FUND_TARGET = "target";
    /** The output of {@link LiquidityFigures#shortfall} that is the expected balance on the first day below zero. */
    String SHORTFALL = "shortfall";
    /** The output of {@link LiquidityFigures#shortfall} that is the lowest expected balance. */
    String LOWEST_BALANCE = "lowest";
    /** The output of an {@link InsuranceFigures.Contract#premium}. */
    String PREMIUM = "premium";
    /** The output of {@link #emergencyFundTopUp}. */
    String EMERGENCY_FUND_TOP_UP = "amount";
    /** The outputs of a {@link RuleChangeFigures.Change#figures}: the new value and the value it replaces. */
    String RULE_VALUE = "value";
    String RULE_PREVIOUS_VALUE = "previous";

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
     * The average monthly net income at the end of {@code asOf} (function {@code monthlyNetIncome}, same key as in
     * {@link #profileFigures}): the account data a what-if's subsidy eligibility rests on.
     */
    CoreResult netIncome(RequestContext context, EvidenceOrigin origin);

    /**
     * The what-if projection of a monthly amount over whole years (function {@code whatIfProjection}, seed
     * {@code context.seeds().core()}), with the Riester basic allowance if a subsidy was asked and the persona is
     * eligible.
     *
     * @param subsidy {@code null} if the question did not ask for the subsidy, else whether the persona is eligible
     * @param netIncomeFigure the Core figure the eligibility rests on, or {@code null}
     * @throws de.financetool.knowledge.NoValidRuleParameterException if a Rule Parameter has no version valid at
     *     {@code asOf}; nothing is stored
     */
    WhatIfFigures whatIf(RequestContext context, EvidenceOrigin origin, BigDecimal monthly, int years,
            BigDecimal initial, Boolean subsidy, SourcedNumber netIncomeFigure);

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
     * The measures the Autonomy Gate decides on (FR-35), from the given snapshot only: liquid assets, Data Confidence
     * (thresholds from {@code context.thresholds()}), the overnight deposit after moving {@code amount} out of
     * {@code sourceAccountId} (unchanged if that is not the overnight deposit) and the emergency fund target. Nothing
     * is stored; no transaction is needed.
     *
     * @param snapshot the persona's bank snapshot at {@code context.asOf()}, read once by the caller
     * @param amount {@code null} counts as 0
     * @throws IllegalStateException if the persona has no current account
     */
    GateMeasures gateMeasures(RequestContext context, BankSnapshot snapshot, UUID sourceAccountId, BigDecimal amount);

    /**
     * The balances of both accounts at the end of {@code context.asOf()} before and after moving {@code amount} from
     * {@code fromAccountId} to {@code toAccountId}, for the confirmation dialog. Nothing is stored.
     *
     * @param snapshot the persona's bank snapshot at {@code context.asOf()}, read once by the caller
     * @throws IllegalArgumentException if an account is not in the snapshot
     */
    TransferBalances transferBalances(RequestContext context, BankSnapshot snapshot, UUID fromAccountId,
            UUID toAccountId, BigDecimal amount);

    /**
     * The emergency fund top-up at the end of {@code asOf} (function {@code emergencyFundTopUp}): the gap to the
     * target, at most the monthly net income minus the monthly expenses, never negative. Its inputs carry the keys of
     * {@link #profileFigures}.
     *
     * @throws de.financetool.knowledge.NoValidRuleParameterException if an emergency-fund Rule Parameter has no
     *     version valid at {@code asOf}
     */
    CoreResult emergencyFundTopUp(RequestContext context, EvidenceOrigin origin);

    /**
     * The Rule Parameter versions whose validity starts after {@code since} and on or before {@code asOf}, each with
     * the value it replaces (function {@code ruleParameterChange}), and every announced rule change with whether its
     * keys have versions valid on its effective date.
     */
    RuleChangeFigures ruleChanges(RequestContext context, EvidenceOrigin origin, LocalDate since);

    /** The ids of the persona's current account and overnight deposit at {@code asOf}; nothing is stored. */
    AccountIds accountIds(RequestContext context);

    /**
     * The Core's refusal without inputs: checks that every subsidy rule has a version valid on {@code date}.
     *
     * @throws de.financetool.knowledge.NoValidRuleParameterException if one has none
     */
    void requireSubsidyRules(LocalDate date);
}
