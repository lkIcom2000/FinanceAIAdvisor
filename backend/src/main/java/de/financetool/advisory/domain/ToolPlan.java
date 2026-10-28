package de.financetool.advisory.domain;

import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 3's plan (AD-3): a pure function of the extracted parameters that names the whitelisted tool a calculation
 * needs and the parameters it runs with. A port of {@code _tool_plan} in {@code experiments/finlab/pipeline.py}; the
 * LLM never takes part. Only the withdrawal duration is planned so far.
 */
public final class ToolPlan {

    /** The calculation tools a plan can name; the registry maps each to its Core function. */
    public enum Tool {
        WITHDRAWAL_DURATION("withdrawalDuration"),
        SAVINGS_PROJECTION("savingsProjection");

        private final String function;

        Tool(String function) {
            this.function = function;
        }

        /** The spec's function id. */
        public String function() {
            return function;
        }

        public static Optional<Tool> ofFunction(String function) {
            for (Tool tool : values()) {
                if (tool.function.equals(function)) {
                    return Optional.of(tool);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * The Core figures a personal explanation question ({@code INFORM}, subject {@code self}) runs on, one rule per
     * topic (Story 13.4, FR-62); the registry maps each to its Core call.
     */
    public enum PersonalFigures {
        /** The emergency fund: current reserve, monthly expenses and target range. */
        EMERGENCY_FUND,
        /** The goal projection of the persona's investments and its starting totals. */
        GOAL_PROJECTION,
        /** The coverage summary: duplicate and missing essential cover. */
        COVERAGE;

        private static final Map<String, PersonalFigures> BY_TOPIC = Map.of(
                "liquidity_management", EMERGENCY_FUND,
                "retirement_strategy", GOAL_PROJECTION,
                "protection_needs", COVERAGE,
                "protection_duplicate", COVERAGE);

        /** The rule for {@code topic}, if it has one. */
        public static Optional<PersonalFigures> forTopic(String topic) {
            return Optional.ofNullable(topic == null ? null : BY_TOPIC.get(topic));
        }
    }

    /** The personal figures the turn runs on: an {@code INFORM} turn about the user on a topic with a rule. */
    public static Optional<PersonalFigures> personalFigures(MessageClass messageClass, NluExtraction extraction) {
        return messageClass == MessageClass.INFORM && extraction.subject().equals("self")
                ? PersonalFigures.forTopic(extraction.topic()) : Optional.empty();
    }

    public static final String PORTFOLIO = "portfolio";
    public static final String MONTHLY_WITHDRAWAL = "monthlyWithdrawal";
    public static final String ANNUAL_RATE = "annualRate";
    public static final String MONTHLY_AMOUNT = "monthlyAmount";
    public static final String YEARS = "years";
    public static final String INITIAL = "initial";
    public static final String STATE_SUBSIDY = "subsidy";
    /** The longest what-if horizon in years ({@code WhatIfProjection.MAX_YEARS}). */
    static final int MAX_YEARS = 60;

    /** Bounds a changed value must keep; the extracted values must keep them too to be computable. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");
    static final BigDecimal MAX_RATE = new BigDecimal("0.2");
    static final BigDecimal MIN_RATE = MAX_RATE.negate();

    /** The message speaks of the persona's own investments without naming an amount. */
    static final Pattern OWN_SAVINGS = Pattern.compile(
            "\\bmy (savings|portfolio|investments?|capital|assets|money)\\b", Pattern.CASE_INSENSITIVE);

    private static final int FLAGS_CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    /** Subsidy wording in English and German. */
    static final Pattern SUBSIDY = Pattern.compile("\\b(riester\\w*|subsid\\w*|altersvorsorgedepot\\w*|zulage\\w*|förder\\w*|gefördert\\w*)",
            FLAGS_CI);
    /** The message asks about the next year, or about the new subsidised product (it starts with the reform in 2027). */
    static final Pattern NEXT_YEAR = Pattern.compile("\\b(next year|coming year|following year|(nächst|kommend)\\w* jahr\\w*"
            + "|new subsidi[sz]ed|neu\\w* gefördert\\w*|altersvorsorgedepot\\w*)", FLAGS_CI);
    /** The next year named as a number ("in 2027", "ab 2027"); a later year is a horizon, not an ask. */
    static final Pattern NAMED_YEAR = Pattern.compile("\\b(in|for|from|by|ab|für|im|bis)\\s+(20\\d\\d)\\b", FLAGS_CI);

    /**
     * A planned calculation before account data is read.
     *
     * @param stated the parameters stated in the message, by spec key
     * @param portfolioFromAccount whether the portfolio is the persona's investments today (Core investment totals)
     * @param subsidyAsked whether a what-if asks for the state subsidy (eligibility from account data)
     */
    public record Planned(Tool tool, Map<String, BigDecimal> stated, boolean portfolioFromAccount,
            boolean subsidyAsked) {

        public Planned {
            Objects.requireNonNull(tool, "tool");
            stated = Map.copyOf(stated);
        }

        public Planned(Tool tool, Map<String, BigDecimal> stated, boolean portfolioFromAccount) {
            this(tool, stated, portfolioFromAccount, false);
        }
    }

    private ToolPlan() {
    }

    /**
     * The calculation the extraction asks for, if it has every parameter: withdrawal duration when the topic is
     * {@code withdrawal_duration} or portfolio and monthly withdrawal are extracted, and portfolio, withdrawal and
     * rate are known. A missing portfolio comes from the persona's investments if the message speaks of them.
     * Otherwise a what-if savings projection when the topic is {@code savings_projection} or a monthly contribution
     * and years are extracted, and both are known; a stated start above zero joins it, subsidy wording adds the
     * subsidy.
     */
    public static Optional<Planned> plan(NluExtraction extraction, String message) {
        Map<String, Double> p = extraction.computationParams();
        if (!p.values().stream().allMatch(Double::isFinite)) {
            return Optional.empty();
        }
        boolean withdrawal = extraction.topic().equals("withdrawal_duration")
                || p.containsKey("portfolio") && p.containsKey("monthly_withdrawal");
        if (!withdrawal) {
            return savings(extraction, p, message);
        }
        if (!p.containsKey("monthly_withdrawal") || !p.containsKey("annual_rate")) {
            return Optional.empty();
        }
        BigDecimal monthly = money(p.get("monthly_withdrawal"));
        BigDecimal rate = rate(p.get("annual_rate"));
        // A portfolio of zero is the model's placeholder for "not stated", not a value.
        if (p.getOrDefault("portfolio", 0.0) > 0) {
            BigDecimal portfolio = money(p.get("portfolio"));
            return valid(portfolio, monthly, rate) ? Optional.of(new Planned(Tool.WITHDRAWAL_DURATION,
                    Map.of(PORTFOLIO, portfolio, MONTHLY_WITHDRAWAL, monthly, ANNUAL_RATE, rate), false))
                    : Optional.empty();
        }
        if (!OWN_SAVINGS.matcher(message).find() || !valid(BigDecimal.ONE, monthly, rate)) {
            return Optional.empty();
        }
        return Optional.of(new Planned(Tool.WITHDRAWAL_DURATION,
                Map.of(MONTHLY_WITHDRAWAL, monthly, ANNUAL_RATE, rate), true));
    }

    private static Optional<Planned> savings(NluExtraction extraction, Map<String, Double> p, String message) {
        boolean savings = extraction.topic().equals("savings_projection")
                || p.containsKey("monthly_contribution") && p.containsKey("years");
        if (!savings || !p.containsKey("monthly_contribution") || !p.containsKey("years")) {
            return Optional.empty();
        }
        BigDecimal monthly = money(p.get("monthly_contribution"));
        double years = p.get("years");
        if (!amount(monthly) || years != Math.rint(years) || years < 1 || years > MAX_YEARS) {
            return Optional.empty();
        }
        Map<String, BigDecimal> stated = new java.util.LinkedHashMap<>();
        stated.put(MONTHLY_AMOUNT, monthly);
        stated.put(YEARS, BigDecimal.valueOf((long) years));
        // A start of zero is the model's placeholder for "not stated", as for the portfolio.
        if (p.getOrDefault("initial", 0.0) > 0) {
            BigDecimal initial = money(p.get("initial"));
            if (!amount(initial)) {
                return Optional.empty();
            }
            stated.put(INITIAL, initial);
        }
        return Optional.of(new Planned(Tool.SAVINGS_PROJECTION, stated, false, SUBSIDY.matcher(message).find()));
    }

    /**
     * The first day of a later year a subsidy question asks about, whose subsidy rules the Core must have before Fine
     * may answer (UJ-5): the topic is {@code subsidy_eligibility} or the message uses subsidy wording, and it names
     * the next year (in words or as a number) or the new subsidised product. Empty otherwise.
     */
    public static Optional<LocalDate> laterSubsidyYear(NluExtraction extraction, String message, LocalDate asOf) {
        if (!extraction.topic().equals("subsidy_eligibility") && !SUBSIDY.matcher(message).find()) {
            return Optional.empty();
        }
        int year = asOf.getYear();
        int later = NEXT_YEAR.matcher(message).find() ? year + 1 : Integer.MAX_VALUE;
        Matcher named = NAMED_YEAR.matcher(message);
        while (named.find()) {
            int value = Integer.parseInt(named.group(2));
            if (value == year + 1) {
                later = value;
            }
        }
        return later == Integer.MAX_VALUE ? Optional.empty() : Optional.of(LocalDate.of(later, 1, 1));
    }

    /**
     * The spec the proposal card shows.
     *
     * @param accountPortfolio the persona's investments today; required if the plan takes the portfolio from
     *     account data
     */
    public static SimulationSpec spec(Planned planned, SourcedNumber accountPortfolio) {
        return spec(planned, accountPortfolio, null);
    }

    /**
     * The spec the proposal card shows.
     *
     * @param accountPortfolio the persona's investments today; required if the plan takes the portfolio from
     *     account data
     * @param netIncome the persona's average monthly net income (Core figure); required if a what-if asks for the
     *     subsidy: above zero means eligible
     */
    public static SimulationSpec spec(Planned planned, SourcedNumber accountPortfolio, SourcedNumber netIncome) {
        List<SimulationSpec.Parameter> parameters = new ArrayList<>();
        if (planned.tool() == Tool.SAVINGS_PROJECTION) {
            parameters.add(stated(MONTHLY_AMOUNT, "Monthly amount", planned, Unit.EUR));
            parameters.add(stated(YEARS, "Duration", planned, Unit.YEARS));
            if (planned.stated().containsKey(INITIAL)) {
                parameters.add(stated(INITIAL, "Amount at the start", planned, Unit.EUR));
            }
            if (planned.subsidyAsked()) {
                Objects.requireNonNull(netIncome, "netIncome");
                boolean eligible = netIncome.value().signum() > 0;
                parameters.add(new SimulationSpec.Parameter(STATE_SUBSIDY,
                        eligible ? "State subsidy (you receive a salary)"
                                : "State subsidy (no salary income found, so not eligible)",
                        eligible ? BigDecimal.ONE : BigDecimal.ZERO, Unit.FLAG, SimulationSpec.Source.ACCOUNT_DATA,
                        netIncome.provId()));
            }
            return new SimulationSpec(planned.tool().function(), parameters);
        }
        if (planned.portfolioFromAccount()) {
            Objects.requireNonNull(accountPortfolio, "accountPortfolio");
            parameters.add(new SimulationSpec.Parameter(PORTFOLIO, "Starting portfolio", accountPortfolio.value(),
                    Unit.EUR, SimulationSpec.Source.ACCOUNT_DATA, accountPortfolio.provId()));
        } else {
            parameters.add(stated(PORTFOLIO, "Starting portfolio", planned, Unit.EUR));
        }
        parameters.add(stated(MONTHLY_WITHDRAWAL, "Monthly withdrawal", planned, Unit.EUR));
        parameters.add(stated(ANNUAL_RATE, "Assumed annual return", planned, Unit.FRACTION));
        return new SimulationSpec(planned.tool().function(), parameters);
    }

    /**
     * What is wrong with the values of a spec about to be calculated, in plain words; empty if it can run: a known
     * tool, a portfolio and a withdrawal above zero and at most 1,000,000,000 EUR, a return between -20 % and 20 %.
     */
    public static List<String> problems(SimulationSpec spec) {
        Optional<Tool> tool = Tool.ofFunction(spec.function());
        if (tool.isEmpty()) {
            return List.of("This calculation is not available.");
        }
        List<String> problems = new ArrayList<>();
        if (tool.get() == Tool.SAVINGS_PROJECTION) {
            if (!amount(spec.parameter(MONTHLY_AMOUNT).value())) {
                problems.add("The monthly amount must be more than 0 and at most 1,000,000,000 EUR.");
            }
            BigDecimal years = spec.parameter(YEARS).value();
            if (years.stripTrailingZeros().scale() > 0 || years.compareTo(BigDecimal.ONE) < 0
                    || years.compareTo(BigDecimal.valueOf(MAX_YEARS)) > 0) {
                problems.add("The duration must be a whole number of years from 1 to " + MAX_YEARS + ".");
            }
            if (has(spec, INITIAL) && !amount(spec.parameter(INITIAL).value())) {
                problems.add("The amount at the start must be more than 0 and at most 1,000,000,000 EUR.");
            }
            if (has(spec, STATE_SUBSIDY) && spec.parameter(STATE_SUBSIDY).source() == SimulationSpec.Source.CHANGED) {
                problems.add("The state subsidy can't be changed here. Please ask a new question instead.");
            }
            return problems;
        }
        BigDecimal portfolio = spec.parameter(PORTFOLIO).value();
        BigDecimal monthly = spec.parameter(MONTHLY_WITHDRAWAL).value();
        BigDecimal rate = spec.parameter(ANNUAL_RATE).value();
        if (!amount(portfolio)) {
            problems.add("The starting portfolio must be more than 0 and at most 1,000,000,000 EUR.");
        }
        if (!amount(monthly)) {
            problems.add("The monthly withdrawal must be more than 0 and at most 1,000,000,000 EUR.");
        }
        if (rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
            problems.add("The assumed annual return must be between -20 % and 20 %.");
        }
        return problems;
    }

    /** Whether the spec has the parameter {@code key}. */
    public static boolean has(SimulationSpec spec, String key) {
        return spec.parameters().stream().anyMatch(p -> p.key().equals(key));
    }

    private static boolean valid(BigDecimal portfolio, BigDecimal monthly, BigDecimal rate) {
        return amount(portfolio) && amount(monthly) && rate.compareTo(MIN_RATE) >= 0 && rate.compareTo(MAX_RATE) <= 0;
    }

    private static boolean amount(BigDecimal value) {
        return value.signum() > 0 && value.compareTo(MAX_AMOUNT) <= 0;
    }

    private static SimulationSpec.Parameter stated(String key, String label, Planned planned, Unit unit) {
        return new SimulationSpec.Parameter(key, label, planned.stated().get(key), unit,
                SimulationSpec.Source.QUESTION, null);
    }

    /** An amount in EUR with cents, as the Core shows money. */
    private static BigDecimal money(double value) {
        return new BigDecimal(Double.toString(value)).setScale(2, RoundingMode.HALF_UP);
    }

    /** A rate as the shortest decimal of the extracted fraction ({@code 0.04}, never {@code 0.0400}). */
    private static BigDecimal rate(double value) {
        BigDecimal rate = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        return rate.scale() < 0 ? rate.setScale(0) : rate;
    }
}
