package de.financetool.core.application;

import de.financetool.audit.EvidenceItem;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.audit.EvidenceStore;
import de.financetool.audit.Provenance;
import de.financetool.bank.AccountType;
import de.financetool.bank.AssetClass;
import de.financetool.bank.BankReadPort;
import de.financetool.bank.BankSnapshot;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.CashFlowForecast;
import de.financetool.core.domain.CheckResult;
import de.financetool.core.domain.CoreComputation;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.DataConfidence;
import de.financetool.core.domain.EmergencyFundTarget;
import de.financetool.core.domain.Figure;
import de.financetool.core.domain.FigureKey;
import de.financetool.core.domain.LedgerEntry;
import de.financetool.core.domain.Liquidity;
import de.financetool.core.domain.Investments;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.MonteCarloProjection;
import de.financetool.core.domain.RuleParameterValue;
import de.financetool.core.domain.ShortfallDetection;
import de.financetool.core.domain.VariableSpending;
import de.financetool.core.domain.WealthFigures;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.knowledge.RuleParameter;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    @Transactional(propagation = Propagation.MANDATORY)
    public CoreResult currentAccountBalance(RequestContext context, EvidenceOrigin origin) {
        BankSnapshot snapshot = bank.snapshot(context.personaId(), context.asOf());
        return balance(context, origin, snapshot, currentAccount(snapshot, context), AccountBalance.Account.CURRENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
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

        List<LedgerEntry> currentEntries = snapshot.transactions().stream()
                .filter(t -> t.accountId().equals(current.id()))
                .map(CoreGatewayService::entry)
                .toList();
        CoreResult variable = record(context, origin, VariableSpending.monthly(currentEntries, asOf), List.of(),
                Map.of());

        RuleParameterValue band = parameter(CashFlowForecast.BAND, asOf);
        RuleParameterValue monthsMin = parameter(EmergencyFundTarget.MONTHS_MIN, asOf);
        RuleParameterValue monthsMax = parameter(EmergencyFundTarget.MONTHS_MAX, asOf);

        CoreResult emergencyFund = record(context, origin,
                EmergencyFundTarget.of(overnightFigure, currentEntries, asOf, monthsMin, monthsMax), List.of(),
                Map.of(monthsMin.key(), monthsMin, monthsMax.key(), monthsMax));

        List<CashFlowForecast.Premium> premiums = snapshot.contracts().stream()
                .filter(c -> c.premium() != null && c.paymentInterval() != null && c.nextDueDate() != null
                        && current.id().equals(c.debitAccountId()))
                .map(c -> new CashFlowForecast.Premium(premiumDescription(c), c.premium(),
                        c.paymentInterval().months(), c.nextDueDate()))
                .toList();
        CashFlowForecast forecast = CashFlowForecast.of(currentFigure, variable.number(VariableSpending.OUTPUT),
                CashFlowForecast.recurrences(currentEntries, asOf), premiums, band, asOf);
        List<LedgerEntry> allEntries = snapshot.transactions().stream().map(CoreGatewayService::entry).toList();
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
    @Transactional(propagation = Propagation.MANDATORY)
    public WealthFigures wealth(RequestContext context, EvidenceOrigin origin) {
        LocalDate asOf = context.asOf();
        BankSnapshot snapshot = bank.snapshot(context.personaId(), asOf);

        List<WealthFigures.Holding> holdings = snapshot.holdings().stream()
                .map(h -> new WealthFigures.Holding(h.name(), assetClass(h.assetClass()), h.units(),
                        record(context, origin, Investments.holdingValue(h.id().toString(), h.name(), h.units(),
                                h.unitPrice(), h.valuationDate()), List.of(), Map.of())))
                .toList();
        List<WealthFigures.SavingsPlan> plans = snapshot.savingsPlans().stream()
                .filter(p -> !p.startDate().isAfter(asOf))
                .map(p -> new WealthFigures.SavingsPlan(p.name(),
                        record(context, origin, Investments.savingsPlanAmount(p.id().toString(), p.name(),
                                p.amount(), p.dayOfMonth(), p.startDate()), List.of(), Map.of()),
                        p.dayOfMonth()))
                .toList();
        if (holdings.isEmpty() && plans.isEmpty()) {
            return new WealthFigures(List.of(), List.of(), null, null, List.of(), List.of());
        }

        CoreResult totals = record(context, origin, Investments.totals(
                holdings.stream().map(h -> h.value().number(Investments.HOLDING_OUTPUT)).toList(),
                holdings.stream().map(WealthFigures.Holding::name).toList(),
                plans.stream().map(p -> p.amount().number(Investments.PLAN_OUTPUT)).toList(),
                plans.stream().map(WealthFigures.SavingsPlan::name).toList()), List.of(), Map.of());

        RuleParameterValue horizon = parameter(MonteCarloProjection.HORIZON, asOf);
        RuleParameterValue ter = parameter(MonteCarloProjection.TER, asOf);
        RuleParameterValue inflation = parameter(MonteCarloProjection.INFLATION, asOf);
        Map<String, RuleParameterValue> parameters = new HashMap<>(Map.of(horizon.key(), horizon,
                ter.key(), ter, inflation.key(), inflation));
        MonteCarloProjection.ReturnModel model;
        Optional<IndexReturns.Series> series = indexReturns.series()
                .filter(s -> s.monthlyReturns().size() > MonteCarloProjection.MIN_BOOTSTRAP_MONTHS);
        if (series.isPresent()) {
            model = new MonteCarloProjection.Bootstrap(series.get().source(), series.get().monthlyReturns());
        } else {
            RuleParameterValue mean = parameter(MonteCarloProjection.MEAN_RETURN, asOf);
            RuleParameterValue volatility = parameter(MonteCarloProjection.VOLATILITY, asOf);
            parameters.put(mean.key(), mean);
            parameters.put(volatility.key(), volatility);
            model = new MonteCarloProjection.Parametric(mean, volatility);
        }
        MonteCarloProjection projection = MonteCarloProjection.project(totals.number(Investments.OUTPUT_INITIAL),
                totals.number(Investments.OUTPUT_MONTHLY), horizon, MonteCarloProjection.DEFAULT_PATHS,
                context.seeds().core(), model, ter, inflation);
        CoreResult projectionResult = record(context, origin, projection.computation(), List.of(), parameters);
        return new WealthFigures(holdings, plans, totals, projectionResult, projection.points(),
                projection.assumptions());
    }

    private static BankSnapshot.Account currentAccount(BankSnapshot snapshot, RequestContext context) {
        return snapshot.accounts().stream()
                .filter(a -> a.type() == AccountType.CURRENT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No current account for " + context.personaId()));
    }

    private CoreResult balance(RequestContext context, EvidenceOrigin origin, BankSnapshot snapshot,
            BankSnapshot.Account account, AccountBalance.Account kind) {
        List<AccountBalance.Booking> bookings = snapshot.transactions().stream()
                .filter(t -> t.accountId().equals(account.id()))
                .map(t -> new AccountBalance.Booking(t.bookingDate(), t.amount()))
                .toList();
        return record(context, origin, AccountBalance.of(kind, account.openingDate(), account.openingBalance(),
                bookings, context.asOf()), List.of(), Map.of());
    }

    private static LedgerEntry entry(BankSnapshot.Transaction t) {
        return new LedgerEntry(t.bookingDate(), t.amount(), t.category() == null ? null : t.category().name(),
                t.reference() != null ? t.reference() : t.counterparty(), t.recurrenceId(), t.contractId() != null);
    }

    private static String assetClass(AssetClass assetClass) {
        return switch (assetClass) {
            case EQUITY_ETF -> "Equity ETF";
            case BOND_ETF -> "Bond ETF";
            case MONEY_MARKET_FUND -> "Money market fund";
        };
    }

    private static String premiumDescription(BankSnapshot.Contract contract) {
        String coverage = switch (contract.coverage()) {
            case LIABILITY -> "Private liability insurance";
            case CAR -> "Car insurance";
            case HOUSEHOLD_CONTENTS -> "Household contents insurance";
            case OCCUPATIONAL_DISABILITY -> "Occupational disability insurance";
            case LEGAL_PROTECTION -> "Legal protection insurance";
            case RIESTER_PENSION -> "Riester pension";
        };
        return coverage + ", " + contract.paymentInterval().name().toLowerCase(Locale.ROOT) + " premium";
    }

    /** The Rule Parameter version valid at {@code asOf}; the port refuses a key without one. */
    private RuleParameterValue parameter(String key, LocalDate asOf) {
        RuleParameter parameter = ruleParameters.valid(key, asOf);
        return new RuleParameterValue(parameter.key(), parameter.value(), parameter.version(),
                parameter.validFrom(), parameter.validTo());
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
