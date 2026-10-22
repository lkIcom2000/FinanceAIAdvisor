package de.financetool.core.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The keyed results behind the Liquidity view, from one {@code CoreGateway} call.
 *
 * @param overnightDeposit {@code null} if the persona has no overnight deposit
 * @param forecast outputs {@link CashFlowForecast#OUTPUT_EXPECTED}, {@code _LOW}, {@code _HIGH}; the expected end
 *        balance's key is the forecast's key
 * @param points the forecast's daily balances
 * @param shortfall outputs {@link ShortfallDetection#OUTPUT_LOWEST} and, if there is a shortfall,
 *        {@link ShortfallDetection#OUTPUT_SHORTFALL}
 * @param shortfallDate the first day below zero, or {@code null}
 */
@NamedInterface("results")
public record LiquidityFigures(CoreResult currentAccount, CoreResult overnightDeposit, CoreResult liquidAssets,
        CoreResult variableSpending, CoreResult emergencyFund, CoreResult forecast, List<CashFlowForecast.Point> points,
        List<String> forecastAssumptions, CoreResult shortfall, LocalDate shortfallDate, LocalDate lowestDate) {

    public LiquidityFigures {
        Objects.requireNonNull(currentAccount, "currentAccount");
        Objects.requireNonNull(liquidAssets, "liquidAssets");
        Objects.requireNonNull(variableSpending, "variableSpending");
        Objects.requireNonNull(emergencyFund, "emergencyFund");
        Objects.requireNonNull(forecast, "forecast");
        Objects.requireNonNull(shortfall, "shortfall");
        Objects.requireNonNull(lowestDate, "lowestDate");
        points = List.copyOf(points);
        forecastAssumptions = List.copyOf(forecastAssumptions);
    }
}
