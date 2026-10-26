package de.financetool.core.web;

import de.financetool.core.application.ShowHomeOverviewUseCase;
import de.financetool.core.application.ShowLiquidityOverviewUseCase;
import de.financetool.core.application.ShowWealthOverviewUseCase;
import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.CashFlowForecast;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.CoverageFlag;
import de.financetool.core.domain.EmergencyFundTarget;
import de.financetool.core.domain.InsuranceFigures;
import de.financetool.core.domain.InsurancePremium;
import de.financetool.core.domain.Investments;
import de.financetool.core.domain.Liquidity;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.MonteCarloProjection;
import de.financetool.core.domain.ShortfallDetection;
import de.financetool.core.domain.VariableSpending;
import de.financetool.core.domain.WealthFigures;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import de.financetool.shared.web.FigureView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Financial Overview: every figure carries its figure key for "Why?". Persona from the session only. */
@Tag(name = "overview", description = "The Financial Overview")
@RestController
class OverviewController {

    private static final String DECIMAL = "^-?\\d+(\\.\\d+)?$";

    @Schema(description = "A balance on a day, e.g. the first forecast day below zero")
    record DatedFigure(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView amount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^-?\\d+$", example = "-90",
                    description = "The amount rounded to whole euros (half up), for the chart marker and running text")
            String wholeEuros) {

        static DatedFigure of(SourcedNumber number, LocalDate date) {
            return new DatedFigure(FigureView.of(number), date,
                    number.value().setScale(0, RoundingMode.HALF_UP).toPlainString());
        }
    }

    @Schema(description = "The emergency fund (overnight deposit) against its target")
    record EmergencyFund(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView current,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Minimum target") FigureView target,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView targetMax,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Months of expenses in the target")
            FigureView months,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView monthlyExpenses) {
    }

    @Schema(description = "One forecast day: the expected balance and its possible range")
    record ForecastPoint(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL) String low,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL) String expected,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL) String high) {
    }

    @Schema(description = "The daily current account forecast over three months (EUR)")
    record Forecast(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN,
                    description = "Key of the forecast's provenance") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView expectedEnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ForecastPoint> points,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> assumptions) {
    }

    record LiquidityOverview(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView currentAccount,
            @Schema(description = "Absent if there is no overnight deposit") FigureView overnightDeposit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView liquidAssets,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView variableSpending,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EmergencyFund emergencyFund,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Forecast forecast,
            @Schema(description = "The first day the expected balance is below zero; absent if it never is")
            DatedFigure shortfall,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DatedFigure lowestBalance) {
    }

    @Schema(description = "An insurance or pension contract on which you are insured")
    record InsuranceContract(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Contract type in plain words",
                    example = "Private liability insurance") String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String policyholder,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether you are co-insured on someone else's policy") boolean coInsured,
            @Schema(description = "The premium per payment interval; absent if you don't pay it") FigureView premium,
            @Schema(description = "Absent without a premium", allowableValues = {"monthly", "quarterly", "yearly"})
            String paymentInterval,
            @Schema(description = "The next due date after today; absent without a premium") LocalDate nextDueDate) {
    }

    enum FlagKind {
        DUPLICATE_COVERAGE, MISSING_COVER
    }

    @Schema(description = "A plain-words note on your insurance: cover you may pay twice for, or essential cover"
            + " you don't have")
    record InsuranceFlag(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FlagKind kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The cover in plain words",
                    example = "liability") String coverage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN,
                    description = "Key of the check's provenance") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Ids of every contract involved (empty for a missing cover)")
            List<String> contractIds) {
    }

    record Insurance(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InsuranceContract> contracts,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<InsuranceFlag> flags) {
    }

    record HomeOverview(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView currentAccount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The lowest expected balance in the next three months") DatedFigure lowestBalance,
            @Schema(description = "The first day the expected balance is below zero; absent if it never is")
            DatedFigure shortfall,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EmergencyFund emergencyFund,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Insurance insurance) {
    }

    @Schema(description = "A holding in the securities account")
    record Holding(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Asset class in plain words",
                    example = "Equity ETF") String assetClass,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL, example = "17.842300")
            String units,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView value) {
    }

    @Schema(description = "An active savings plan")
    record SavingsPlan(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView monthlyAmount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "31") int dayOfMonth) {
    }

    @Schema(description = "The projection's percentiles at the end of one year (EUR)")
    record ProjectionPoint(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int year,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL) String p10,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL) String p50,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = DECIMAL) String p90) {
    }

    @Schema(description = "The horizon percentiles in whole euros (half up) with thousands separators and a real"
            + " minus sign, for the summary sentence")
    record WholeEuros(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "38,120") String p10,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "71,450") String p50,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "139,800") String p90) {
    }

    @Schema(description = "Where the investments may lead: a range, not a promise")
    record Projection(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN,
                    description = "Key of the projection's provenance (its middle outcome)") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int years,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView p10,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView p50,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView p90,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView paidIn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView p50TodaysMoney,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView startingValue,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FigureView monthlyContribution,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProjectionPoint> points,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> assumptions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WholeEuros wholeEuros) {
    }

    record WealthOverview(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Holding> holdings,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SavingsPlan> savingsPlans,
            @Schema(description = "Absent without holdings and savings plans") Projection projection) {
    }

    private final ShowHomeOverviewUseCase showHome;
    private final ShowLiquidityOverviewUseCase showLiquidity;
    private final ShowWealthOverviewUseCase showWealth;

    OverviewController(ShowHomeOverviewUseCase showHome, ShowLiquidityOverviewUseCase showLiquidity,
            ShowWealthOverviewUseCase showWealth) {
        this.showHome = showHome;
        this.showLiquidity = showLiquidity;
        this.showWealth = showWealth;
    }

    @Operation(summary = "The Home tab's summary figures")
    @GetMapping("/api/overview/home")
    HomeOverview home(@Parameter(hidden = true) PersonaScope scope) {
        ShowHomeOverviewUseCase.HomeOverview home = showHome.show(scope);
        LiquidityFigures f = home.liquidity();
        InsuranceFigures insurance = home.insurance();
        List<InsuranceContract> contracts = insurance.contracts().stream()
                .map(c -> new InsuranceContract(c.contract().id(), c.contract().cover().typeName(),
                        c.contract().policyholder(), c.contract().coInsured(),
                        c.premium() == null ? null : FigureView.of(c.premium().number(InsurancePremium.OUTPUT)),
                        c.premium() == null ? null : c.contract().intervalWords(), c.nextDueDate()))
                .toList();
        List<InsuranceFlag> flags = new ArrayList<>();
        insurance.duplicateFlags().forEach(flag -> flags.add(flag(flag, insurance.duplicates())));
        insurance.missingFlags().forEach(flag -> flags.add(flag(flag, insurance.missing())));
        return new HomeOverview(
                FigureView.of(f.currentAccount().number(AccountBalance.OUTPUT)),
                DatedFigure.of(f.shortfall().number(ShortfallDetection.OUTPUT_LOWEST), f.lowestDate()),
                shortfall(f),
                emergencyFund(f.emergencyFund()),
                new Insurance(contracts, flags));
    }

    private static InsuranceFlag flag(CoverageFlag flag, CoreResult result) {
        return new InsuranceFlag(FlagKind.valueOf(flag.kind().name()), flag.cover().plainWords(),
                result.number(flag.outputKey()).provId(), flag.contractIds());
    }

    private static DatedFigure shortfall(LiquidityFigures f) {
        return f.shortfallDate() == null ? null
                : DatedFigure.of(f.shortfall().number(ShortfallDetection.OUTPUT_SHORTFALL), f.shortfallDate());
    }

    private static EmergencyFund emergencyFund(CoreResult fund) {
        return new EmergencyFund(
                FigureView.of(fund.number(EmergencyFundTarget.OUTPUT_CURRENT)),
                FigureView.of(fund.number(EmergencyFundTarget.OUTPUT_TARGET)),
                FigureView.of(fund.number(EmergencyFundTarget.OUTPUT_TARGET_MAX)),
                FigureView.of(fund.number(EmergencyFundTarget.OUTPUT_MONTHS)),
                FigureView.of(fund.number(EmergencyFundTarget.OUTPUT_MONTHLY_EXPENSES)));
    }

    @Operation(summary = "The Liquidity tab: balances, emergency fund and the three-month cash-flow forecast")
    @GetMapping("/api/overview/liquidity")
    LiquidityOverview liquidity(@Parameter(hidden = true) PersonaScope scope) {
        LiquidityFigures f = showLiquidity.show(scope);
        SourcedNumber expectedEnd = f.forecast().number(CashFlowForecast.OUTPUT_EXPECTED);
        return new LiquidityOverview(
                FigureView.of(f.currentAccount().number(AccountBalance.OUTPUT)),
                f.overnightDeposit() == null ? null : FigureView.of(f.overnightDeposit().number(AccountBalance.OUTPUT)),
                FigureView.of(f.liquidAssets().number(Liquidity.OUTPUT)),
                FigureView.of(f.variableSpending().number(VariableSpending.OUTPUT)),
                emergencyFund(f.emergencyFund()),
                new Forecast(expectedEnd.provId(), FigureView.of(expectedEnd),
                        f.points().stream().map(p -> new ForecastPoint(p.date(), p.low().toPlainString(),
                                p.expected().toPlainString(), p.high().toPlainString())).toList(),
                        f.forecastAssumptions()),
                shortfall(f),
                DatedFigure.of(f.shortfall().number(ShortfallDetection.OUTPUT_LOWEST), f.lowestDate()));
    }

    @Operation(summary = "The Wealth & Retirement tab: holdings, savings plans and the goal projection")
    @GetMapping("/api/overview/wealth")
    WealthOverview wealth(@Parameter(hidden = true) PersonaScope scope) {
        WealthFigures f = showWealth.show(scope);
        List<Holding> holdings = f.holdings().stream()
                .map(h -> new Holding(h.name(), h.assetClass(), h.units().toPlainString(),
                        FigureView.of(h.value().number(Investments.HOLDING_OUTPUT))))
                .toList();
        List<SavingsPlan> plans = f.savingsPlans().stream()
                .map(p -> new SavingsPlan(p.name(), FigureView.of(p.amount().number(Investments.PLAN_OUTPUT)),
                        p.dayOfMonth()))
                .toList();
        if (f.projection() == null) {
            return new WealthOverview(holdings, plans, null);
        }
        CoreResult projection = f.projection();
        SourcedNumber p10 = projection.number(MonteCarloProjection.OUTPUT_P10);
        SourcedNumber p50 = projection.number(MonteCarloProjection.OUTPUT_P50);
        SourcedNumber p90 = projection.number(MonteCarloProjection.OUTPUT_P90);
        return new WealthOverview(holdings, plans, new Projection(p50.provId(), f.points().getLast().year(),
                FigureView.of(p10), FigureView.of(p50), FigureView.of(p90),
                FigureView.of(projection.number(MonteCarloProjection.OUTPUT_PAID_IN)),
                FigureView.of(projection.number(MonteCarloProjection.OUTPUT_P50_REAL)),
                FigureView.of(f.totals().number(Investments.OUTPUT_INITIAL)),
                FigureView.of(f.totals().number(Investments.OUTPUT_MONTHLY)),
                f.points().stream().map(p -> new ProjectionPoint(p.year(), p.p10().toPlainString(),
                        p.p50().toPlainString(), p.p90().toPlainString())).toList(),
                f.assumptions(),
                new WholeEuros(wholeEuros(p10.value()), wholeEuros(p50.value()), wholeEuros(p90.value()))));
    }

    /** "38,120", "−1,240": whole euros (half up) with thousands separators and a real minus sign. */
    static String wholeEuros(BigDecimal amount) {
        BigDecimal whole = amount.setScale(0, RoundingMode.HALF_UP);
        String digits = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
                .format(whole.abs());
        return whole.signum() < 0 ? "\u2212" + digits : digits;
    }
}
