package de.financetool.core.application;

import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.audit.EvidenceStore;
import de.financetool.audit.Provenance;
import de.financetool.bank.AccountType;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.AccountIds;
import de.financetool.core.domain.EmergencyFundTopUp;
import de.financetool.core.domain.RuleChangeFigures;
import de.financetool.core.domain.TransferBalances;
import de.financetool.core.domain.RuleParameterChange;
import de.financetool.core.domain.CashFlowForecast;
import de.financetool.core.domain.CategorySelection;
import de.financetool.core.domain.CostCalculation;
import de.financetool.core.domain.DemandsAndNeeds;
import de.financetool.core.domain.NeedsFigures;
import de.financetool.core.domain.RecommendationFigures;
import de.financetool.core.domain.CheckResult;
import de.financetool.core.domain.CoreComputation;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.DataConfidence;
import de.financetool.core.domain.EmergencyFundTarget;
import de.financetool.core.domain.Figure;
import de.financetool.core.domain.DuplicateCoverage;
import de.financetool.core.domain.FigureKey;
import de.financetool.core.domain.GateMeasures;
import de.financetool.core.domain.InsuranceContract;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.InsurancePremium;
import de.financetool.core.domain.LedgerEntry;
import de.financetool.core.domain.Liquidity;
import de.financetool.core.domain.Investments;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.MissingEssentialCover;
import de.financetool.core.domain.MonteCarloProjection;
import de.financetool.core.domain.MonthlyNetIncome;
import de.financetool.core.domain.ProfileFigures;
import de.financetool.core.domain.RiesterSubsidy;
import de.financetool.core.domain.RuleParameterValue;
import de.financetool.core.domain.ShortfallDetection;
import de.financetool.core.domain.VariableSpending;
import de.financetool.core.domain.WealthFigures;
import de.financetool.core.domain.WhatIfFigures;
import de.financetool.core.domain.WhatIfProjection;
import de.financetool.core.domain.WithdrawalDuration;
import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.knowledge.RuleParameter;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads the snapshot, runs the pure function, keys its outputs and stores their provenance (AD-2, AD-21). */
@Service
class CoreGatewayService implements CoreGateway {

    private final BankReadPort bank;
    private final EvidenceStore evidence;
    private final RuleParamPort ruleParameters;
    private final IndexReturns indexReturns;

    CoreGatewayService(BankReadPort bank, EvidenceStore evidence, RuleParamPort ruleParameters,
            IndexReturns indexReturns) {
        this.bank = bank;
        this.evidence = evidence;
        this.ruleParameters = ruleParameters;
        this.indexReturns = indexReturns;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public CoreResult currentAccountBalance(RequestContext context, EvidenceOrigin origin) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        return balance(context, origin, snapshot, currentAccount(snapshot, context), AccountBalance.Account.CURRENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public LiquidityFigures liquidity(RequestContext context, EvidenceOrigin origin) {
        LocalDate asOf = context.asOf();
        BankSnapshot snapshot = bank.snapshot(context.personaId(), asOf);
        BankSnapshot.Account current = currentAccount(snapshot, context);
        CoreResult currentBalance = balance(context, origin, snapshot, current, AccountBalance.Account.CURRENT);
        CoreResult overnightBalance = snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.OVERNIGHT)
                .findFirst()
                .map(a -> balance(context, origin, snapshot, a, AccountBalance.Account.OVERNIGHT))
                .orElse(null);
        SourcedNumber currentFigure = currentBalance.number(AccountBalance.OUTPUT);
        SourcedNumber overnightFigure = overnightBalance == null ? null
                : overnightBalance.number(AccountBalance.OUTPUT);

        CoreResult liquidAssets = record(context, origin, Liquidity.liquidAssets(currentFigure, overnightFigure),
                List.of(), Map.of());

        List<LedgerEntry> currentEntries = BankMapping.entries(snapshot, current.id());
        CoreResult variable = record(context, origin, VariableSpending.monthly(currentEntries, asOf), List.of(),
                Map.of());

        RuleParameterValue band = parameter(CashFlowForecast.BAND, asOf);
        RuleParameterValue monthsMin = parameter(EmergencyFundTarget.MONTHS_MIN, asOf);
        RuleParameterValue monthsMax = parameter(EmergencyFundTarget.MONTHS_MAX, asOf);

        CoreResult emergencyFund = record(context, origin,
                EmergencyFundTarget.of(overnightFigure, currentEntries, asOf, monthsMin, monthsMax), List.of(),
                Map.of(monthsMin.key(), monthsMin, monthsMax.key(), monthsMax));

        List<CashFlowForecast.Premium> premiums = BankMapping.premiums(snapshot, current.id());
        CashFlowForecast forecast = CashFlowForecast.of(currentFigure, variable.number(VariableSpending.OUTPUT),
                CashFlowForecast.recurrences(currentEntries, asOf), premiums, band, asOf);
        List<LedgerEntry> allEntries = BankMapping.entries(snapshot, null);
        CheckResult confidence = DataConfidence.of(allEntries, asOf,
                context.thresholds().dataConfidenceMinCoverage(),
                context.thresholds().dataConfidenceMaxGapDays()).toCheck();
        CoreResult forecastResult = record(context, origin, forecast.computation(), List.of(confidence),
                Map.of(band.key(), band));

        ShortfallDetection.Result shortfall = ShortfallDetection.detect(forecast,
                forecastResult.number(CashFlowForecast.OUTPUT_EXPECTED));
        CoreResult shortfallResult = record(context, origin, shortfall.computation(), List.of(confidence),
                Map.of());

        return new LiquidityFigures(currentBalance, overnightBalance, liquidAssets, variable, emergencyFund,
                forecastResult, forecast.points(), forecast.assumptions(), shortfallResult,
                shortfall.shortfallDate(), shortfall.lowestDate());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public ProfileFigures profileFigures(RequestContext context, EvidenceOrigin origin) {
        LocalDate asOf = context.asOf();
        BankSnapshot snapshot = bank.snapshot(context.personaId(), asOf);
        BankSnapshot.Account current = currentAccount(snapshot, context);
        SourcedNumber overnightFigure = snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.OVERNIGHT)
                .findFirst()
                .map(a -> balance(context, origin, snapshot, a, AccountBalance.Account.OVERNIGHT)
                        .number(AccountBalance.OUTPUT))
                .orElse(null);
        List<LedgerEntry> currentEntries = BankMapping.entries(snapshot, current.id());
        RuleParameterValue monthsMin = parameter(EmergencyFundTarget.MONTHS_MIN, asOf);
        RuleParameterValue monthsMax = parameter(EmergencyFundTarget.MONTHS_MAX, asOf);
        CoreResult emergencyFund = record(context, origin,
                EmergencyFundTarget.of(overnightFigure, currentEntries, asOf, monthsMin, monthsMax), List.of(),
                Map.of(monthsMin.key(), monthsMin, monthsMax.key(), monthsMax));
        CoreResult netIncome = record(context, origin, MonthlyNetIncome.of(currentEntries, asOf), List.of(),
                Map.of());
        return new ProfileFigures(netIncome, emergencyFund);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public WealthFigures wealth(RequestContext context, EvidenceOrigin origin) {
        LocalDate asOf = context.asOf();
        BankSnapshot snapshot = bank.snapshot(context.personaId(), asOf);

        List<WealthFigures.Holding> holdings = holdings(context, origin, snapshot);
        List<WealthFigures.SavingsPlan> plans = plans(context, origin, snapshot);
        if (holdings.isEmpty() && plans.isEmpty()) {
            return new WealthFigures(List.of(), List.of(), null, null, List.of(), List.of());
        }
        CoreResult totals = totals(context, origin, holdings, plans);

        RuleParameterValue horizon = parameter(MonteCarloProjection.HORIZON, asOf);
        RuleParameterValue ter = parameter(MonteCarloProjection.TER, asOf);
        RuleParameterValue inflation = parameter(MonteCarloProjection.INFLATION, asOf);
        Map<String, RuleParameterValue> parameters = new HashMap<>(Map.of(horizon.key(), horizon,
                ter.key(), ter, inflation.key(), inflation));
        MonteCarloProjection.ReturnModel model = returnModel(asOf, parameters);
        MonteCarloProjection projection = MonteCarloProjection.project(totals.number(Investments.OUTPUT_INITIAL),
                totals.number(Investments.OUTPUT_MONTHLY), horizon, MonteCarloProjection.DEFAULT_PATHS,
                context.seeds().core(), model, ter, inflation);
        CoreResult projectionResult = record(context, origin, projection.computation(), List.of(), parameters);
        return new WealthFigures(holdings, plans, totals, projectionResult, projection.points(),
                projection.assumptions());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public NeedsFigures needs(RequestContext context, EvidenceOrigin origin, Map<String, ?> needsProfile) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        DemandsAndNeeds.Result needs = DemandsAndNeeds.of(needsProfile, BankMapping.contracts(snapshot),
                context.personaId().value());
        return new NeedsFigures(record(context, origin, needs.computation(), List.of(), Map.of()), needs.statement());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public InsuranceFigures insurance(RequestContext context, EvidenceOrigin origin) {
        LocalDate asOf = context.asOf();
        BankSnapshot snapshot = bank.snapshot(context.personaId(), asOf);
        List<InsuranceContract> contracts = BankMapping.contracts(snapshot);
        List<InsuranceFigures.Contract> rows = contracts.stream()
                .map(c -> {
                    if (c.premium() == null) {
                        return new InsuranceFigures.Contract(c, null, null);
                    }
                    InsurancePremium.Result premium = InsurancePremium.of(c, asOf);
                    return new InsuranceFigures.Contract(c,
                            record(context, origin, premium.computation(), List.of(), Map.of()),
                            premium.nextDueDate());
                })
                .toList();
        DuplicateCoverage.Result duplicates = DuplicateCoverage.detect(contracts, context.personaId().value());
        MissingEssentialCover.Result missing = MissingEssentialCover.detect(contracts);
        return new InsuranceFigures(rows,
                record(context, origin, duplicates.computation(), List.of(), Map.of()), duplicates.flags(),
                record(context, origin, missing.computation(), List.of(), Map.of()), missing.flags());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public Optional<CoreResult> investmentTotals(RequestContext context, EvidenceOrigin origin) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        List<WealthFigures.Holding> holdings = holdings(context, origin, snapshot);
        List<WealthFigures.SavingsPlan> plans = plans(context, origin, snapshot);
        return holdings.isEmpty() && plans.isEmpty() ? Optional.empty()
                : Optional.of(totals(context, origin, holdings, plans));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public CoreResult withdrawalDuration(RequestContext context, EvidenceOrigin origin, BigDecimal portfolio,
            SourcedNumber portfolioFigure, BigDecimal monthlyWithdrawal, BigDecimal annualRate) {
        return record(context, origin,
                WithdrawalDuration.compute(portfolio, portfolioFigure, monthlyWithdrawal, annualRate), List.of(),
                Map.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public CoreResult riesterSubsidy(RequestContext context, EvidenceOrigin origin, BigDecimal priorYearGrossIncome,
            List<Integer> childrenBirthYears) {
        Map<String, RuleParameterValue> parameters = subsidyRules(context.asOf());
        return record(context, origin, RiesterSubsidy.compute(priorYearGrossIncome, childrenBirthYears, parameters),
                List.of(), parameters);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public RecommendationFigures recommendation(RequestContext context, EvidenceOrigin origin,
            Map<String, ?> profile, String topic, boolean withCosts) {
        LocalDate asOf = context.asOf();
        // every Rule Parameter first, so a missing one stores nothing
        Map<String, RuleParameterValue> parameters = new LinkedHashMap<>();
        CategorySelection.parameterKeys().forEach(key -> parameters.put(key, parameter(key, asOf)));
        Map<String, RuleParameterValue> costParameters = new LinkedHashMap<>();
        if (withCosts) {
            List.of(CostCalculation.ETF_TER, CostCalculation.ACTIVE_TER, CostCalculation.FRONT_LOAD)
                    .forEach(key -> costParameters.put(key, parameter(key, asOf)));
        }
        CategorySelection.Selected selected = CategorySelection.of(profile, topic, asOf, parameters);
        CoreResult selection = record(context, origin, selected.computation(), List.of(), parameters);
        CoreResult costs = null;
        if (withCosts) {
            costs = record(context, origin, CostCalculation.of(CostCalculation.amount(profile), CostCalculation.YEARS,
                    costParameters.get(CostCalculation.ETF_TER), costParameters.get(CostCalculation.ACTIVE_TER),
                    costParameters.get(CostCalculation.FRONT_LOAD)), List.of(), costParameters);
        }
        return new RecommendationFigures(selection, selected.selection(), costs);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CoreResult netIncome(RequestContext context, EvidenceOrigin origin) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        BankSnapshot.Account current = currentAccount(snapshot, context);
        return record(context, origin, MonthlyNetIncome.of(BankMapping.entries(snapshot, current.id()),
                context.asOf()), List.of(), Map.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public WhatIfFigures whatIf(RequestContext context, EvidenceOrigin origin, BigDecimal monthly, int years,
            BigDecimal initial, Boolean subsidy, SourcedNumber netIncomeFigure) {
        LocalDate asOf = context.asOf();
        // every Rule Parameter first, so a missing one stores nothing
        RuleParameterValue ter = parameter(MonteCarloProjection.TER, asOf);
        RuleParameterValue inflation = parameter(MonteCarloProjection.INFLATION, asOf);
        Map<String, RuleParameterValue> parameters = new HashMap<>(Map.of(ter.key(), ter,
                inflation.key(), inflation));
        MonteCarloProjection.ReturnModel model = returnModel(asOf, parameters);
        WhatIfProjection.Subsidy asked = null;
        if (Boolean.TRUE.equals(subsidy)) {
            RuleParameterValue basic = parameter(RiesterSubsidy.BASIC, asOf);
            RuleParameterValue floor = parameter(RiesterSubsidy.FLOOR, asOf);
            parameters.put(basic.key(), basic);
            parameters.put(floor.key(), floor);
            asked = new WhatIfProjection.Subsidy(true, netIncomeFigure, basic, floor);
        } else if (subsidy != null) {
            asked = new WhatIfProjection.Subsidy(false, netIncomeFigure, null, null);
        }
        WhatIfProjection projection = WhatIfProjection.project(monthly, years, initial, asked,
                MonteCarloProjection.DEFAULT_PATHS, context.seeds().core(), model, ter, inflation);
        return new WhatIfFigures(record(context, origin, projection.computation(), List.of(), parameters),
                projection.points(), projection.assumptions());
    }

    /**
     * The return model of a projection: a bootstrap of the historical index series if it is long enough, else normal
     * returns from the Rule Parameters, which join {@code parameters}.
     */
    private MonteCarloProjection.ReturnModel returnModel(LocalDate asOf, Map<String, RuleParameterValue> parameters) {
        Optional<IndexReturns.Series> series = indexReturns.series()
                .filter(s -> s.monthlyReturns().size() > MonteCarloProjection.MIN_BOOTSTRAP_MONTHS);
        if (series.isPresent()) {
            return new MonteCarloProjection.Bootstrap(series.get().source(), series.get().monthlyReturns());
        }
        RuleParameterValue mean = parameter(MonteCarloProjection.MEAN_RETURN, asOf);
        RuleParameterValue volatility = parameter(MonteCarloProjection.VOLATILITY, asOf);
        parameters.put(mean.key(), mean);
        parameters.put(volatility.key(), volatility);
        return new MonteCarloProjection.Parametric(mean, volatility);
    }

    @Override
    public TransferBalances transferBalances(RequestContext context, BankSnapshot snapshot, UUID fromAccountId,
            UUID toAccountId, BigDecimal amount) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(amount, "amount");
        BigDecimal from = accountBalance(snapshot, fromAccountId, context.asOf());
        BigDecimal to = accountBalance(snapshot, toAccountId, context.asOf());
        return new TransferBalances(from, from.subtract(amount), to, to.add(amount));
    }

    private static BigDecimal accountBalance(BankSnapshot snapshot, UUID accountId, LocalDate asOf) {
        BankSnapshot.Account account = snapshot.accounts().stream()
                .filter(a -> a.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no account " + accountId + " in the snapshot"));
        return AccountBalance.of(account.openingDate(), account.openingBalance(),
                BankMapping.bookings(snapshot, account.id()), asOf).outputs().getFirst().value();
    }

    @Override
    public GateMeasures gateMeasures(RequestContext context, BankSnapshot snapshot, UUID sourceAccountId,
            BigDecimal amount) {
        Objects.requireNonNull(snapshot, "snapshot");
        LocalDate asOf = context.asOf();
        BankSnapshot.Account current = currentAccount(snapshot, context);
        Optional<BankSnapshot.Account> overnight = snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.OVERNIGHT)
                .findFirst();
        SourcedNumber currentFigure = keyed(context, AccountBalance.of(AccountBalance.Account.CURRENT,
                current.openingDate(), current.openingBalance(), BankMapping.bookings(snapshot, current.id()), asOf))
                .number(AccountBalance.OUTPUT);
        SourcedNumber overnightFigure = overnight
                .map(a -> keyed(context, AccountBalance.of(AccountBalance.Account.OVERNIGHT, a.openingDate(),
                        a.openingBalance(), BankMapping.bookings(snapshot, a.id()), asOf))
                        .number(AccountBalance.OUTPUT))
                .orElse(null);
        BigDecimal liquidAssets = Liquidity.liquidAssets(currentFigure, overnightFigure).outputs().getFirst().value();

        DataConfidence.Result confidence = DataConfidence.of(BankMapping.entries(snapshot, null), asOf,
                context.thresholds().dataConfidenceMinCoverage(), context.thresholds().dataConfidenceMaxGapDays());

        BigDecimal overnightNow = overnightFigure == null ? new BigDecimal("0.00") : overnightFigure.value();
        boolean fromOvernight = overnight.isPresent() && overnight.get().id().equals(sourceAccountId);
        BigDecimal overnightAfter = fromOvernight && amount != null ? overnightNow.subtract(amount) : overnightNow;

        BigDecimal target;
        try {
            RuleParameterValue monthsMin = parameter(EmergencyFundTarget.MONTHS_MIN, asOf);
            RuleParameterValue monthsMax = parameter(EmergencyFundTarget.MONTHS_MAX, asOf);
            CoreComputation fund = EmergencyFundTarget.of(overnightFigure, BankMapping.entries(snapshot, current.id()),
                    asOf, monthsMin, monthsMax);
            target = fund.outputs().stream()
                    .filter(f -> f.outputKey().equals(EmergencyFundTarget.OUTPUT_TARGET))
                    .findFirst().orElseThrow().value();
        } catch (NoValidRuleParameterException e) {
            target = null;
        }
        return new GateMeasures(liquidAssets, confidence.high(), confidence.coverage(), confidence.longestGapDays(),
                overnightAfter, target);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = NoValidRuleParameterException.class)
    public CoreResult emergencyFundTopUp(RequestContext context, EvidenceOrigin origin) {
        ProfileFigures figures = profileFigures(context, origin);
        CoreResult fund = figures.emergencyFund();
        return record(context, origin, EmergencyFundTopUp.of(fund.number(EmergencyFundTarget.OUTPUT_CURRENT),
                fund.number(EmergencyFundTarget.OUTPUT_TARGET), figures.netIncome().number(MonthlyNetIncome.OUTPUT),
                fund.number(EmergencyFundTarget.OUTPUT_MONTHLY_EXPENSES)), List.of(), Map.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RuleChangeFigures ruleChanges(RequestContext context, EvidenceOrigin origin, LocalDate since) {
        List<RuleChangeFigures.Change> changes = ruleParameters.startingBetween(since, context.asOf()).stream()
                .sorted(Comparator.comparing(RuleParameter::validFrom).thenComparing(RuleParameter::key))
                .map(version -> {
                    RuleParameterValue current = value(version);
                    RuleParameterValue previous = ruleParameters.previous(version).map(CoreGatewayService::value)
                            .orElse(null);
                    CoreResult figures = record(context, origin,
                            RuleParameterChange.of(current, previous, Unit.valueOf(version.unit())), List.of(),
                            Map.of(current.key(), current));
                    return new RuleChangeFigures.Change(version.key(), RuleParameterChange.plainName(version.key()),
                            version.version(), version.validFrom(),
                            figures);
                })
                .toList();
        List<RuleChangeFigures.Announcement> announcements = ruleParameters.announcements().stream()
                .map(a -> new RuleChangeFigures.Announcement(a.id(), a.topic(), a.title(), a.keys(), a.announcedOn(),
                        a.effectiveFrom(), a.source(), allValid(a.keys(), a.effectiveFrom())))
                .toList();
        return new RuleChangeFigures(changes, announcements);
    }

    @Override
    public AccountIds accountIds(RequestContext context) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        UUID overnight = snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.OVERNIGHT)
                .map(BankSnapshot.Account::id)
                .findFirst()
                .orElse(null);
        return new AccountIds(currentAccount(snapshot, context).id(), overnight);
    }

    private boolean allValid(List<String> keys, LocalDate date) {
        try {
            keys.forEach(key -> ruleParameters.valid(key, date));
            return !keys.isEmpty();
        } catch (NoValidRuleParameterException e) {
            return false;
        }
    }

    private static RuleParameterValue value(RuleParameter parameter) {
        return new RuleParameterValue(parameter.key(), parameter.value(), parameter.version(),
                parameter.validFrom(), parameter.validTo());
    }

    @Override
    public void requireSubsidyRules(LocalDate date) {
        subsidyRules(date);
    }

    /** Every Riester Rule Parameter valid on {@code date}; the port refuses a key without one. */
    private Map<String, RuleParameterValue> subsidyRules(LocalDate date) {
        Map<String, RuleParameterValue> parameters = new LinkedHashMap<>();
        RiesterSubsidy.PARAMETERS.forEach(key -> parameters.put(key, parameter(key, date)));
        return parameters;
    }

    private List<WealthFigures.Holding> holdings(RequestContext context, EvidenceOrigin origin,
            BankSnapshot snapshot) {
        return snapshot.holdings().stream()
                .map(h -> new WealthFigures.Holding(h.name(), BankMapping.assetClass(h.assetClass()), h.units(),
                        record(context, origin, Investments.holdingValue(h.id().toString(), h.name(), h.units(),
                                h.unitPrice(), h.valuationDate()), List.of(), Map.of())))
                .toList();
    }

    /** The savings plans active at {@code asOf}. */
    private List<WealthFigures.SavingsPlan> plans(RequestContext context, EvidenceOrigin origin,
            BankSnapshot snapshot) {
        return snapshot.savingsPlans().stream()
                .filter(p -> !p.startDate().isAfter(context.asOf()))
                .map(p -> new WealthFigures.SavingsPlan(p.name(),
                        record(context, origin, Investments.savingsPlanAmount(p.id().toString(), p.name(),
                                p.amount(), p.dayOfMonth(), p.startDate()), List.of(), Map.of()),
                        p.dayOfMonth()))
                .toList();
    }

    private CoreResult totals(RequestContext context, EvidenceOrigin origin, List<WealthFigures.Holding> holdings,
            List<WealthFigures.SavingsPlan> plans) {
        return record(context, origin, Investments.totals(
                holdings.stream().map(h -> h.value().number(Investments.HOLDING_OUTPUT)).toList(),
                holdings.stream().map(WealthFigures.Holding::name).toList(),
                plans.stream().map(p -> p.amount().number(Investments.PLAN_OUTPUT)).toList(),
                plans.stream().map(WealthFigures.SavingsPlan::name).toList()), List.of(), Map.of());
    }

    private static BankSnapshot.Account currentAccount(BankSnapshot snapshot, RequestContext context) {
        return snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.CURRENT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No current account for " + context.personaId()));
    }

    private CoreResult balance(RequestContext context, EvidenceOrigin origin, BankSnapshot snapshot,
            BankSnapshot.Account account, AccountBalance.Account kind) {
        List<AccountBalance.Booking> bookings = BankMapping.bookings(snapshot, account.id());
        return record(context, origin, AccountBalance.of(kind, account.openingDate(), account.openingBalance(),
                bookings, context.asOf()), List.of(), Map.of());
    }

    /** The Rule Parameter version valid at {@code asOf}; the port refuses a key without one. */
    private RuleParameterValue parameter(String key, LocalDate asOf) {
        return value(ruleParameters.valid(key, asOf));
    }

    /** Keys every output without storing evidence: for intermediate figures nobody is shown. */
    private static CoreResult keyed(RequestContext context, CoreComputation computation) {
        String fingerprint = context.configFingerprint().value();
        List<SourcedNumber> numbers = computation.outputs().stream()
                .map(output -> new SourcedNumber(FigureKey.of(context.personaId(), computation.function(),
                        computation.keyInputs(), context.asOf(), fingerprint, output.outputKey()), output.value(),
                        output.unit(), output.label()))
                .toList();
        return new CoreResult(computation.function(), computation.inputs(), computation.outputs(),
                computation.assumptions(), computation.ruleParamVersions(), numbers);
    }

    /** Keys every output, stores one evidence item per output and returns the keyed result. */
    private CoreResult record(RequestContext context, EvidenceOrigin origin, CoreComputation computation,
            List<CheckResult> checkResults, Map<String, RuleParameterValue> parameters) {
        Objects.requireNonNull(origin, "origin");
        String fingerprint = context.configFingerprint().value();
        Provenance.Source source = new Provenance.Source(Provenance.SourceKind.CORE_FUNCTION,
                "Calculated by Fine's calculation engine (" + computation.plainName()
                        + "), not by the language model. " + computation.basis());
        List<Provenance.Input> inputs = computation.inputs().stream()
                .map(i -> new Provenance.Input(i.label(), i.value(), i.unit(), i.text(), i.provId()))
                .toList();
        List<Provenance.RuleParameter> ruleParameters = computation.ruleParamVersions().entrySet().stream()
                .map(e -> {
                    RuleParameterValue parameter = parameters.get(e.getKey());
                    return new Provenance.RuleParameter(e.getKey(), e.getValue(),
                            parameter == null ? null : parameter.validFrom(),
                            parameter == null ? null : parameter.validTo());
                })
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();

        List<Provenance.Check> checks = checkResults.stream()
                .map(c -> new Provenance.Check(c.id(), c.passed(), c.description()))
                .toList();
        List<SourcedNumber> numbers = new ArrayList<>();
        List<EvidenceItem> items = new ArrayList<>();
        for (Figure output : computation.outputs()) {
            String provId = FigureKey.of(context.personaId(), computation.function(), computation.keyInputs(),
                    context.asOf(), fingerprint, output.outputKey());
            numbers.add(new SourcedNumber(provId, output.value(), output.unit(), output.label()));
            Provenance provenance = new Provenance(provId, computation.function(), source, output.label(),
                    output.value(), output.unit(), context.asOf(), inputs, computation.assumptions(),
                    ruleParameters, checks);
            items.add(new EvidenceItem(provId, context.personaId(), origin, null, provenance));
        }
        evidence.putIfAbsent(items);
        return new CoreResult(computation.function(), computation.inputs(), computation.outputs(),
                computation.assumptions(), computation.ruleParamVersions(), numbers);
    }
}
