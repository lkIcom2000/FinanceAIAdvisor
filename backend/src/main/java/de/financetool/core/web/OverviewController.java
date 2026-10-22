package de.financetool.core.web;

import de.financetool.core.application.ShowHomeOverviewUseCase;
import de.financetool.core.application.ShowLiquidityOverviewUseCase;
import de.financetool.core.domain.AccountBalance;
import de.financetool.core.domain.CashFlowForecast;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.EmergencyFundTarget;
import de.financetool.core.domain.Liquidity;
import de.financetool.core.domain.LiquidityFigures;
import de.financetool.core.domain.ShortfallDetection;
import de.financetool.core.domain.VariableSpending;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Financial Overview: every figure carries its figure key for "Why?". Persona from the session only. */
@Tag(name = "overview", description = "The Financial Overview")
@RestController
class OverviewController {

    private static final String DECIMAL = "^-?\\d+(\\.\\d+)?$";

    @Schema(description = "A figure from Fine's calculation engine with the key of its provenance")
    record Figure(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[0-9a-f]{16}$") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Decimal string",
                    pattern = DECIMAL, example = "1974.00") String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Unit unit) {

        static Figure of(SourcedNumber number) {
            return new Figure(number.provId(), number.label(), number.value().toPlainString(), number.unit());
        }
    }

    record HomeOverview(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure currentAccount) {
    }

    @Schema(description = "A balance on a day, e.g. the first forecast day below zero")
    record DatedFigure(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure amount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^-?\\d+$", example = "-90",
                    description = "The amount rounded to whole euros (half up), for the chart marker and running text")
            String wholeEuros) {

        static DatedFigure of(SourcedNumber number, LocalDate date) {
            return new DatedFigure(Figure.of(number), date,
                    number.value().setScale(0, RoundingMode.HALF_UP).toPlainString());
        }
    }

    @Schema(description = "The emergency fund (overnight deposit) against its target")
    record EmergencyFund(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure current,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Minimum target") Figure target,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure targetMax,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Months of expenses in the target")
            Figure months,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure monthlyExpenses) {
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[0-9a-f]{16}$",
                    description = "Key of the forecast's provenance") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure expectedEnd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ForecastPoint> points,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> assumptions) {
    }

    record LiquidityOverview(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure currentAccount,
            @Schema(description = "Absent if there is no overnight deposit") Figure overnightDeposit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure liquidAssets,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure variableSpending,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EmergencyFund emergencyFund,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Forecast forecast,
            @Schema(description = "The first day the expected balance is below zero; absent if it never is")
            DatedFigure shortfall,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DatedFigure lowestBalance) {
    }

    private final ShowHomeOverviewUseCase showHome;
    private final ShowLiquidityOverviewUseCase showLiquidity;

    OverviewController(ShowHomeOverviewUseCase showHome, ShowLiquidityOverviewUseCase showLiquidity) {
        this.showHome = showHome;
        this.showLiquidity = showLiquidity;
    }

    @Operation(summary = "The Home tab's summary figures")
    @GetMapping("/api/overview/home")
    HomeOverview home(@Parameter(hidden = true) PersonaScope scope) {
        return new HomeOverview(Figure.of(showHome.show(scope).currentAccount()));
    }

    @Operation(summary = "The Liquidity tab: balances, emergency fund and the three-month cash-flow forecast")
    @GetMapping("/api/overview/liquidity")
    LiquidityOverview liquidity(@Parameter(hidden = true) PersonaScope scope) {
        LiquidityFigures f = showLiquidity.show(scope);
        CoreResult fund = f.emergencyFund();
        SourcedNumber expectedEnd = f.forecast().number(CashFlowForecast.OUTPUT_EXPECTED);
        return new LiquidityOverview(
                Figure.of(f.currentAccount().number(AccountBalance.OUTPUT)),
                f.overnightDeposit() == null ? null : Figure.of(f.overnightDeposit().number(AccountBalance.OUTPUT)),
                Figure.of(f.liquidAssets().number(Liquidity.OUTPUT)),
                Figure.of(f.variableSpending().number(VariableSpending.OUTPUT)),
                new EmergencyFund(
                        Figure.of(fund.number(EmergencyFundTarget.OUTPUT_CURRENT)),
                        Figure.of(fund.number(EmergencyFundTarget.OUTPUT_TARGET)),
                        Figure.of(fund.number(EmergencyFundTarget.OUTPUT_TARGET_MAX)),
                        Figure.of(fund.number(EmergencyFundTarget.OUTPUT_MONTHS)),
                        Figure.of(fund.number(EmergencyFundTarget.OUTPUT_MONTHLY_EXPENSES))),
                new Forecast(expectedEnd.provId(), Figure.of(expectedEnd),
                        f.points().stream().map(p -> new ForecastPoint(p.date(), p.low().toPlainString(),
                                p.expected().toPlainString(), p.high().toPlainString())).toList(),
                        f.forecastAssumptions()),
                f.shortfallDate() == null ? null
                        : DatedFigure.of(f.shortfall().number(ShortfallDetection.OUTPUT_SHORTFALL), f.shortfallDate()),
                DatedFigure.of(f.shortfall().number(ShortfallDetection.OUTPUT_LOWEST), f.lowestDate()));
    }
}
