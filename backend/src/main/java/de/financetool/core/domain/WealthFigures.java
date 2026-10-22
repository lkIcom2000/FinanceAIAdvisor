package de.financetool.core.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The keyed results behind the Wealth &amp; Retirement view, from one {@code CoreGateway} call.
 *
 * @param projection outputs of {@link MonteCarloProjection}, or {@code null} without holdings and savings plans
 * @param totals the projection's starting value and monthly contribution ({@link Investments#totals}), or
 *        {@code null} without a projection
 */
public record WealthFigures(List<Holding> holdings, List<SavingsPlan> savingsPlans, CoreResult totals,
        CoreResult projection, List<MonteCarloProjection.Point> points, List<String> assumptions) {

    /** A holding with its keyed value ({@link Investments#HOLDING_OUTPUT}). */
    public record Holding(String name, String assetClass, BigDecimal units, CoreResult value) {

        public Holding {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(assetClass, "assetClass");
            Objects.requireNonNull(units, "units");
            Objects.requireNonNull(value, "value");
        }
    }

    /** An active savings plan with its keyed monthly amount ({@link Investments#PLAN_OUTPUT}). */
    public record SavingsPlan(String name, CoreResult amount, int dayOfMonth) {

        public SavingsPlan {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public WealthFigures {
        holdings = List.copyOf(holdings);
        savingsPlans = List.copyOf(savingsPlans);
        points = List.copyOf(points);
        assumptions = List.copyOf(assumptions);
        if ((projection == null) != (totals == null)) {
            throw new IllegalArgumentException("a projection comes with its totals");
        }
        if (projection != null && (points.isEmpty() || assumptions.isEmpty())) {
            throw new IllegalArgumentException("a projection carries its yearly points and assumptions");
        }
    }
}
