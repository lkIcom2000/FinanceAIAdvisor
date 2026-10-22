package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.springframework.modulith.NamedInterface;

/**
 * Core function {@code monteCarloProjection}: where a starting value plus a monthly contribution may lead after a
 * horizon of whole years, as the 10th, 50th and 90th percentile of simulated paths. Pure and seeded: the only source
 * of randomness is {@code L64X128MixRandom} created from the given seed, so the same inputs and seed give the same
 * result.
 *
 * <p>Model (as {@code experiments/finlab/core.py} {@code monte_carlo}): per path and month, wealth = wealth × (1 + r −
 * TER / 12) + contribution. The monthly return r is either drawn from historical monthly index returns (bootstrap) or
 * normal with mean (1 + μ)^(1/12) − 1 and volatility σ / √12. Percentiles interpolate linearly between the sorted
 * paths. Values are rounded to cents only in the output.
 *
 * <p>A projection without assumptions or without its percentiles fails validation (constructor).
 */
public record MonteCarloProjection(List<Point> points, List<String> assumptions, CoreComputation computation) {

    public static final String FUNCTION = "monteCarloProjection";
    public static final String OUTPUT_P10 = "p10";
    public static final String OUTPUT_P50 = "p50";
    public static final String OUTPUT_P90 = "p90";
    public static final String OUTPUT_PAID_IN = "paidIn";
    public static final String OUTPUT_P50_REAL = "p50Real";
    public static final String MEAN_RETURN = "projection_equity_mean_return";
    public static final String VOLATILITY = "projection_equity_volatility";
    public static final String TER = "reference_ter_world_equity_etf";
    public static final String INFLATION = "projection_inflation";
    public static final String HORIZON = "projection_default_horizon_years";
    public static final int DEFAULT_PATHS = 5_000;
    /** A historical series needs more than this many monthly returns to be bootstrapped. */
    public static final int MIN_BOOTSTRAP_MONTHS = 24;
    static final String ALGORITHM = "L64X128MixRandom";

    /** The 10th, 50th and 90th percentile of all paths at the end of year {@code year}. */
    @NamedInterface("results")
    public record Point(int year, BigDecimal p10, BigDecimal p50, BigDecimal p90) {

        public Point {
            Objects.requireNonNull(p10, "p10");
            Objects.requireNonNull(p50, "p50");
            Objects.requireNonNull(p90, "p90");
        }
    }

    /** How monthly returns are drawn. */
    public sealed interface ReturnModel {
    }

    /** Normal monthly returns from the Rule Parameters {@value #MEAN_RETURN} and {@value #VOLATILITY} (p.a.). */
    public record Parametric(RuleParameterValue meanReturn, RuleParameterValue volatility) implements ReturnModel {

        public Parametric {
            requireKey(meanReturn, MEAN_RETURN);
            requireKey(volatility, VOLATILITY);
        }
    }

    /**
     * Monthly returns drawn with replacement from a historical index series.
     *
     * @param source the series' file name, as shown in the assumptions
     * @param monthlyReturns monthly returns as fractions, more than {@value #MIN_BOOTSTRAP_MONTHS}
     */
    public record Bootstrap(String source, List<BigDecimal> monthlyReturns) implements ReturnModel {

        public Bootstrap {
            Objects.requireNonNull(source, "source");
            monthlyReturns = List.copyOf(monthlyReturns);
            if (monthlyReturns.size() <= MIN_BOOTSTRAP_MONTHS) {
                throw new IllegalArgumentException("a bootstrap needs more than " + MIN_BOOTSTRAP_MONTHS
                        + " monthly returns, got " + monthlyReturns.size());
            }
        }
    }

    public MonteCarloProjection {
        Objects.requireNonNull(computation, "computation");
        points = List.copyOf(points);
        assumptions = List.copyOf(assumptions);
        if (assumptions.isEmpty()) {
            throw new IllegalArgumentException("a projection carries its assumptions");
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a projection is a range: it needs its yearly percentiles");
        }
        List<String> outputs = computation.outputs().stream().map(Figure::outputKey).toList();
        if (!outputs.containsAll(List.of(OUTPUT_P10, OUTPUT_P50, OUTPUT_P90))) {
            throw new IllegalArgumentException("a projection is a range: it needs p10, p50 and p90");
        }
    }

    /**
     * @param initial the value of the investments today (a Core figure)
     * @param monthlyContribution the monthly savings plan contributions (a Core figure)
     * @param horizon the Rule Parameter {@value #HORIZON}: whole years
     * @param paths the number of simulated paths
     * @param seed the seed of the random generator (from {@code RequestContext.seeds().core()})
     * @param ter the Rule Parameter {@value #TER}: yearly fund costs, deducted monthly
     * @param inflation the Rule Parameter {@value #INFLATION}: for the median in today's money
     */
    public static MonteCarloProjection project(SourcedNumber initial, SourcedNumber monthlyContribution,
            RuleParameterValue horizon, int paths, long seed, ReturnModel returnModel, RuleParameterValue ter,
            RuleParameterValue inflation) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(monthlyContribution, "monthlyContribution");
        Objects.requireNonNull(returnModel, "returnModel");
        requireKey(horizon, HORIZON);
        requireKey(ter, TER);
        requireKey(inflation, INFLATION);
        int years = horizon.value().intValueExact();
        if (years < 1) {
            throw new IllegalArgumentException("the horizon is at least one year");
        }
        if (paths < 10) {
            throw new IllegalArgumentException("a projection needs at least 10 paths");
        }
        int months = years * 12;
        double start = initial.value().doubleValue();
        double contribution = monthlyContribution.value().doubleValue();
        double monthlyCost = ter.value().doubleValue() / 12;

        RandomGenerator random = RandomGeneratorFactory.of(ALGORITHM).create(seed);
        double[][] byYear = new double[years][paths];
        switch (returnModel) {
            case Parametric p -> {
                double mu = StrictMath.pow(1 + p.meanReturn().value().doubleValue(), 1.0 / 12) - 1;
                double sigma = p.volatility().value().doubleValue() / StrictMath.sqrt(12);
                for (int path = 0; path < paths; path++) {
                    double wealth = start;
                    for (int month = 1; month <= months; month++) {
                        double r = mu + sigma * random.nextGaussian() - monthlyCost;
                        wealth = wealth * (1 + r) + contribution;
                        if (month % 12 == 0) {
                            byYear[month / 12 - 1][path] = wealth;
                        }
                    }
                }
            }
            case Bootstrap b -> {
                double[] returns = b.monthlyReturns().stream().mapToDouble(BigDecimal::doubleValue).toArray();
                for (int path = 0; path < paths; path++) {
                    double wealth = start;
                    for (int month = 1; month <= months; month++) {
                        double r = returns[random.nextInt(returns.length)] - monthlyCost;
                        wealth = wealth * (1 + r) + contribution;
                        if (month % 12 == 0) {
                            byYear[month / 12 - 1][path] = wealth;
                        }
                    }
                }
            }
        }

        List<Point> points = new ArrayList<>();
        double[] last = null;
        for (int year = 1; year <= years; year++) {
            double[] sorted = byYear[year - 1];
            Arrays.sort(sorted);
            points.add(new Point(year, money(percentile(sorted, 0.10)), money(percentile(sorted, 0.50)),
                    money(percentile(sorted, 0.90))));
            last = sorted;
        }
        Point end = points.getLast();
        BigDecimal paidIn = money(initial.value().add(monthlyContribution.value().multiply(BigDecimal.valueOf(months))));
        BigDecimal deflator = BigDecimal.ONE.add(inflation.value()).pow(years, MathContext.DECIMAL128);
        BigDecimal p50Real = money(BigDecimal.valueOf(percentile(last, 0.50)).divide(deflator, MathContext.DECIMAL128));

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("initial", initial.value());
        keyInputs.put("monthlyContribution", monthlyContribution.value());
        keyInputs.put("years", years);
        keyInputs.put("horizonVersion", horizon.version());
        keyInputs.put("paths", paths);
        keyInputs.put("seed", seed);
        keyInputs.put("ter", ter.value());
        keyInputs.put("terVersion", ter.version());
        keyInputs.put("inflation", inflation.value());
        keyInputs.put("inflationVersion", inflation.version());
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put(horizon.key(), horizon.version());
        versions.put(ter.key(), ter.version());
        versions.put(inflation.key(), inflation.version());

        List<FigureInput> inputs = new ArrayList<>();
        inputs.add(FigureInput.figure("Value of your investments today", initial));
        inputs.add(FigureInput.figure("Monthly savings plan contributions", monthlyContribution));
        inputs.add(FigureInput.number("Horizon", horizon.value(), Unit.YEARS));
        String modelAssumption;
        switch (returnModel) {
            case Parametric p -> {
                keyInputs.put("returnModel", Map.of("kind", "normal", "mean", p.meanReturn().value(),
                        "meanVersion", p.meanReturn().version(), "volatility", p.volatility().value(),
                        "volatilityVersion", p.volatility().version()));
                versions.put(p.meanReturn().key(), p.meanReturn().version());
                versions.put(p.volatility().key(), p.volatility().version());
                inputs.add(FigureInput.number("Average yearly return (assumption)", p.meanReturn().value(),
                        Unit.FRACTION));
                inputs.add(FigureInput.number("Yearly volatility (assumption)", p.volatility().value(),
                        Unit.FRACTION));
                modelAssumption = "Returns: normally distributed monthly returns with an average of "
                        + Wording.percent(p.meanReturn().value()) + " a year and a volatility of "
                        + Wording.percent(p.volatility().value()) + " a year. This is an assumption, not historical"
                        + " data.";
            }
            case Bootstrap b -> {
                keyInputs.put("returnModel", Map.of("kind", "bootstrap", "source", b.source(),
                        "returns", List.copyOf(b.monthlyReturns())));
                inputs.add(FigureInput.text("Historical index returns",
                        b.monthlyReturns().size() + " monthly returns from " + b.source()));
                modelAssumption = "Returns: each month's return is drawn at random from "
                        + b.monthlyReturns().size() + " historical monthly index returns (" + b.source() + ").";
            }
        }
        inputs.add(FigureInput.number("Fund costs per year (TER)", ter.value(), Unit.FRACTION));
        inputs.add(FigureInput.number("Inflation per year (assumption)", inflation.value(), Unit.FRACTION));
        inputs.add(FigureInput.number("Simulated paths", BigDecimal.valueOf(paths), Unit.COUNT));

        List<String> assumptions = List.of(
                modelAssumption,
                "Costs: fund costs of " + Wording.percent(ter.value()) + " a year are deducted every month.",
                "Inflation: " + Wording.percent(inflation.value()) + " a year. The amounts are in future euros"
                        + " before taxes; in today's money the middle outcome is " + Wording.eur(p50Real) + ".",
                "Horizon: " + years + " years, starting from " + Wording.eur(initial.value()) + " with "
                        + Wording.eur(monthlyContribution.value()) + " paid in each month ("
                        + Wording.eur(paidIn) + " in total).",
                "The range comes from " + String.format(Locale.ENGLISH, "%,d", paths)
                        + " simulated paths: 1 in 10 ends lower than the low end and 1 in 10 ends higher than the"
                        + " high end.");

        CoreComputation computation = new CoreComputation(FUNCTION, "goal projection",
                "Based on your holdings and savings plans and the assumptions below.", keyInputs, inputs,
                List.of(new Figure(OUTPUT_P10, "Low end after " + years + " years (1 in 10 chance it's lower)",
                                end.p10(), Unit.EUR),
                        new Figure(OUTPUT_P50, "Middle outcome after " + years + " years", end.p50(), Unit.EUR),
                        new Figure(OUTPUT_P90, "High end after " + years + " years (1 in 10 chance it's higher)",
                                end.p90(), Unit.EUR),
                        new Figure(OUTPUT_PAID_IN, "Total paid in after " + years + " years", paidIn, Unit.EUR),
                        new Figure(OUTPUT_P50_REAL, "Middle outcome in today's money", p50Real, Unit.EUR)),
                assumptions, versions);
        return new MonteCarloProjection(points, assumptions, computation);
    }

    /** Linear interpolation between the closest ranks of a sorted array (numpy's default). */
    static double percentile(double[] sorted, double q) {
        double index = q * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = Math.min(lower + 1, sorted.length - 1);
        double fraction = index - lower;
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction;
    }

    private static BigDecimal money(double value) {
        return money(BigDecimal.valueOf(value));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireKey(RuleParameterValue parameter, String key) {
        Objects.requireNonNull(parameter, key);
        if (!key.equals(parameter.key())) {
            throw new IllegalArgumentException("expected Rule Parameter " + key + ", got " + parameter.key());
        }
    }
}
