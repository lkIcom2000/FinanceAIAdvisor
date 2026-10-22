package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code shortfallDetection}: the first day the expected forecast balance is below zero, and the lowest
 * expected balance with its date (the first day it is reached). Pure.
 */
public final class ShortfallDetection {

    public static final String FUNCTION = "shortfallDetection";
    public static final String OUTPUT_SHORTFALL = "shortfall";
    public static final String OUTPUT_LOWEST = "lowest";

    /**
     * @param shortfallDate the first day below zero, or {@code null} if the expected balance never drops below zero
     * @param lowestDate the first day of the lowest expected balance
     */
    public record Result(LocalDate shortfallDate, LocalDate lowestDate, CoreComputation computation) {

        public Result {
            Objects.requireNonNull(lowestDate, "lowestDate");
            Objects.requireNonNull(computation, "computation");
        }

        public boolean hasShortfall() {
            return shortfallDate != null;
        }
    }

    private ShortfallDetection() {
    }

    /**
     * @param forecast the forecast whose expected line is checked
     * @param forecastFigure the forecast's keyed figure (expected end balance), shown as the input for drill-down
     */
    public static Result detect(CashFlowForecast forecast, SourcedNumber forecastFigure) {
        Objects.requireNonNull(forecast, "forecast");
        Objects.requireNonNull(forecastFigure, "forecastFigure");
        CashFlowForecast.Point shortfall = null;
        CashFlowForecast.Point lowest = null;
        for (CashFlowForecast.Point point : forecast.points()) {
            if (shortfall == null && point.expected().signum() < 0) {
                shortfall = point;
            }
            if (lowest == null || point.expected().compareTo(lowest.expected()) < 0) {
                lowest = point;
            }
        }
        String from = Wording.date(forecast.points().getFirst().date());
        String to = Wording.date(forecast.horizonEnd());

        List<Figure> outputs = new ArrayList<>();
        if (shortfall != null) {
            outputs.add(new Figure(OUTPUT_SHORTFALL, "Forecast balance on " + Wording.date(shortfall.date()),
                    shortfall.expected(), Unit.EUR));
        }
        outputs.add(new Figure(OUTPUT_LOWEST, "Lowest expected balance (" + Wording.date(lowest.date()) + ")",
                lowest.expected(), Unit.EUR));

        CoreComputation computation = new CoreComputation(FUNCTION, "shortfall check",
                "Based on your cash-flow forecast from " + from + " to " + to + ".",
                Map.of("forecast", forecastFigure.provId()),
                List.of(FigureInput.figure("Cash-flow forecast (" + forecastFigure.label().toLowerCase(Locale.ROOT) + ")",
                        forecastFigure)),
                outputs,
                List.of("Uses the expected balance of the forecast, not its possible range.",
                        shortfall != null
                                ? "The balance first drops below zero on " + Wording.date(shortfall.date()) + "."
                                : "The expected balance stays at or above zero until " + to + "."),
                Map.of());
        return new Result(shortfall == null ? null : shortfall.date(), lowest.date(), computation);
    }
}
