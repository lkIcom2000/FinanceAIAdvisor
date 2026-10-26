package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code whatIfProjection} (FR-20, FR-33): where a monthly savings amount stated in a what-if question
 * may lead after a number of whole years, as the 10th, 50th and 90th percentile of simulated paths, optionally with
 * the Riester basic allowance credited at the end of every year. Pure and seeded; the simulation is the goal
 * projection's ({@link MonteCarloProjection#simulate}). A port of {@code monte_carlo} in
 * {@code experiments/finlab/core.py} for the {@code savings_projection} plan; the allowance is an app addition (UJ-3).
 */
public record WhatIfProjection(List<MonteCarloProjection.Point> points, List<String> assumptions,
        CoreComputation computation) {

    public static final String FUNCTION = "whatIfProjection";
    public static final String OUTPUT_P10 = MonteCarloProjection.OUTPUT_P10;
    public static final String OUTPUT_P50 = MonteCarloProjection.OUTPUT_P50;
    public static final String OUTPUT_P90 = MonteCarloProjection.OUTPUT_P90;
    public static final String OUTPUT_PAID_IN = MonteCarloProjection.OUTPUT_PAID_IN;
    public static final String OUTPUT_ALLOWANCES = "allowances";
    public static final String OUTPUT_YEARS = "years";
    public static final String OUTPUT_P50_REAL = MonteCarloProjection.OUTPUT_P50_REAL;
    /** The longest horizon a what-if runs. */
    public static final int MAX_YEARS = 60;

    /**
     * The state subsidy a what-if asked for.
     *
     * @param eligible whether the persona is directly eligible (salary income in the account data)
     * @param netIncome the Core figure the eligibility rests on; {@code null} if the confirmed value no longer matches
     *     a figure
     * @param basicAllowance the Rule Parameter {@value RiesterSubsidy#BASIC}; required if eligible
     * @param floor the Rule Parameter {@value RiesterSubsidy#FLOOR}: the yearly own payment the allowance needs at
     *     least; required if eligible
     */
    public record Subsidy(boolean eligible, SourcedNumber netIncome, RuleParameterValue basicAllowance,
            RuleParameterValue floor) {

        public Subsidy {
            if (eligible) {
                MonteCarloProjection.requireKey(basicAllowance, RiesterSubsidy.BASIC);
                MonteCarloProjection.requireKey(floor, RiesterSubsidy.FLOOR);
            }
        }
    }

    public WhatIfProjection {
        Objects.requireNonNull(computation, "computation");
        points = List.copyOf(points);
        assumptions = List.copyOf(assumptions);
        if (points.isEmpty() || assumptions.isEmpty()) {
            throw new IllegalArgumentException("a what-if is a range with its assumptions");
        }
    }

    /**
     * @param monthly the monthly amount saved, above zero
     * @param years whole years, 1 to {@value #MAX_YEARS}
     * @param initial the amount at the start, zero or more
     * @param subsidy the subsidy asked for, or {@code null} if the question did not ask
     * @param seed the seed of the random generator (from {@code RequestContext.seeds().core()})
     */
    public static WhatIfProjection project(BigDecimal monthly, int years, BigDecimal initial, Subsidy subsidy,
            int paths, long seed, MonteCarloProjection.ReturnModel returnModel, RuleParameterValue ter,
            RuleParameterValue inflation) {
        Objects.requireNonNull(monthly, "monthly");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(returnModel, "returnModel");
        MonteCarloProjection.requireKey(ter, MonteCarloProjection.TER);
        MonteCarloProjection.requireKey(inflation, MonteCarloProjection.INFLATION);
        if (monthly.signum() <= 0 || initial.signum() < 0) {
            throw new IllegalArgumentException("a monthly amount above zero and a start of zero or more");
        }
        if (years < 1 || years > MAX_YEARS) {
            throw new IllegalArgumentException("the horizon is 1 to " + MAX_YEARS + " years");
        }
        if (paths < 10) {
            throw new IllegalArgumentException("a projection needs at least 10 paths");
        }
        int months = years * 12;
        BigDecimal ownPerYear = monthly.multiply(BigDecimal.valueOf(12));
        boolean credited = subsidy != null && subsidy.eligible()
                && ownPerYear.compareTo(subsidy.floor().value()) >= 0;
        BigDecimal allowance = credited ? subsidy.basicAllowance().value() : BigDecimal.ZERO;

        MonteCarloProjection.Simulation simulation = MonteCarloProjection.simulate(initial.doubleValue(),
                monthly.doubleValue(), allowance.doubleValue(), years, paths, seed, returnModel,
                ter.value().doubleValue());
        MonteCarloProjection.Point end = simulation.points().getLast();
        BigDecimal paidIn = MonteCarloProjection.money(initial.add(monthly.multiply(BigDecimal.valueOf(months))));
        BigDecimal allowances = MonteCarloProjection.money(allowance.multiply(BigDecimal.valueOf(years)));
        BigDecimal p50Real = MonteCarloProjection.inTodaysMoney(simulation.endMedian(), inflation.value(), years);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("monthly", monthly);
        keyInputs.put("years", years);
        keyInputs.put("initial", initial);
        keyInputs.put("subsidy", subsidy == null ? "notAsked" : subsidy.eligible() ? "eligible" : "notEligible");
        keyInputs.put("allowance", allowance);
        keyInputs.put("paths", paths);
        keyInputs.put("seed", seed);
        keyInputs.put("ter", ter.value());
        keyInputs.put("terVersion", ter.version());
        keyInputs.put("inflation", inflation.value());
        keyInputs.put("inflationVersion", inflation.version());
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put(ter.key(), ter.version());
        versions.put(inflation.key(), inflation.version());

        List<FigureInput> inputs = new ArrayList<>();
        inputs.add(FigureInput.number("Monthly amount", monthly, Unit.EUR));
        inputs.add(FigureInput.number("Duration", BigDecimal.valueOf(years), Unit.YEARS));
        inputs.add(FigureInput.number("Amount at the start", initial, Unit.EUR));
        if (subsidy != null && subsidy.netIncome() != null) {
            inputs.add(FigureInput.figure("Average monthly net income (eligibility)", subsidy.netIncome()));
        }
        if (subsidy != null && subsidy.eligible()) {
            keyInputs.put("allowanceVersion", subsidy.basicAllowance().version());
            keyInputs.put("floor", subsidy.floor().value());
            keyInputs.put("floorVersion", subsidy.floor().version());
            versions.put(subsidy.basicAllowance().key(), subsidy.basicAllowance().version());
            versions.put(subsidy.floor().key(), subsidy.floor().version());
            inputs.add(FigureInput.number("Basic allowance per year", subsidy.basicAllowance().value(), Unit.EUR));
            inputs.add(FigureInput.number("Minimum own payment per year for the allowance", subsidy.floor().value(),
                    Unit.EUR));
        }
        String modelAssumption = MonteCarloProjection.describeModel(returnModel, keyInputs, versions, inputs);
        inputs.add(FigureInput.number("Fund costs per year (TER)", ter.value(), Unit.FRACTION));
        inputs.add(FigureInput.number("Inflation per year (assumption)", inflation.value(), Unit.FRACTION));
        inputs.add(FigureInput.number("Simulated paths", BigDecimal.valueOf(paths), Unit.COUNT));

        List<String> assumptions = List.of(
                modelAssumption,
                MonteCarloProjection.costsAssumption(ter),
                MonteCarloProjection.inflationAssumption(inflation, p50Real),
                "Horizon: " + years + " years, starting from " + Wording.eur(initial) + " with "
                        + Wording.eur(monthly) + " paid in each month (" + Wording.eur(paidIn) + " in total).",
                subsidyAssumption(subsidy, credited),
                MonteCarloProjection.rangeAssumption(paths));

        List<Figure> outputs = new ArrayList<>(List.of(
                new Figure(OUTPUT_P10, "Low end after " + years + " years (1 in 10 chance it's lower)", end.p10(),
                        Unit.EUR),
                new Figure(OUTPUT_P50, "Middle outcome after " + years + " years", end.p50(), Unit.EUR),
                new Figure(OUTPUT_P90, "High end after " + years + " years (1 in 10 chance it's higher)", end.p90(),
                        Unit.EUR),
                new Figure(OUTPUT_PAID_IN, "Your own payments after " + years + " years", paidIn, Unit.EUR)));
        if (credited) {
            outputs.add(new Figure(OUTPUT_ALLOWANCES, "State allowances after " + years + " years", allowances,
                    Unit.EUR));
        }
        outputs.add(new Figure(OUTPUT_YEARS, "Duration", BigDecimal.valueOf(years), Unit.YEARS));
        outputs.add(new Figure(OUTPUT_P50_REAL, "Middle outcome in today's money", p50Real, Unit.EUR));

        CoreComputation computation = new CoreComputation(FUNCTION, "what-if projection",
                "Based on the amounts in your question and the assumptions below.", keyInputs, inputs, outputs,
                assumptions, versions);
        return new WhatIfProjection(simulation.points(), assumptions, computation);
    }

    private static String subsidyAssumption(Subsidy subsidy, boolean credited) {
        if (subsidy == null) {
            return "State subsidy: not included, as the question did not ask for it.";
        }
        if (!subsidy.eligible()) {
            return "State subsidy: not included, as your account data shows no salary income; direct eligibility"
                    + " needs a job with statutory pension insurance.";
        }
        RuleParameterValue basic = subsidy.basicAllowance();
        String rule = "the subsidy rule " + basic.key() + " version " + basic.version()
                + (basic.validTo() == null ? "" : ", valid until " + Wording.date(basic.validTo()));
        if (!credited) {
            return "State subsidy: not included, as the yearly own payment stays below the minimum of "
                    + Wording.eur(subsidy.floor().value()) + " (" + rule + ").";
        }
        return "State subsidy: the basic allowance of " + Wording.eur(basic.value()) + " a year (" + rule
                + ") is added at the end of each year. This assumes you are directly eligible (you receive a"
                + " salary), your own payments reach the minimum own contribution (a share of your previous year's"
                + " gross income, which your account data does not show) and the rules stay as they are today."
                + " Child allowances and the tax deduction are not included.";
    }
}
