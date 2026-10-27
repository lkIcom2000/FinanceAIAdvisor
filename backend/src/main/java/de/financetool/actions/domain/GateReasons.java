package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The customer texts of a capped or refused Action (FR-39, UX-DR25): the reason with the user's own numbers and a
 * fixed "Why?" explanation per criterion. Plain words, never the criterion id or internal vocabulary.
 */
public final class GateReasons {

    /** The Action needs a Rule Parameter that has no version valid at the date. */
    public static final String RULE_PARAMETER_MISSING = "rule_parameter_missing";

    /** At confirmation the source account no longer holds the amount. */
    public static final String SOURCE_NOT_COVERING = "source_not_covering";

    /** At confirmation an account of the transfer no longer exists. */
    public static final String ACCOUNT_MISSING = "account_missing";

    /** At confirmation a later Action of the same type waits for the OK and replaces this one. */
    public static final String SUPERSEDED = "superseded";

    /** At confirmation the emergency fund needs less than the top-up's amount to reach its target. */
    public static final String NO_LONGER_NEEDED = "no_longer_needed";

    private static final char MINUS = '−';

    private GateReasons() {
    }

    /**
     * The reason for the deciding criterion ({@link #deciding}), or {@code null} without criteria.
     *
     * @param amount the Action's amount; {@code null} counts as 0
     * @param mandate the covering rule's bounds, read only for {@code amount_above_mandate_bounds}
     */
    public static String reason(List<String> criteria, BigDecimal amount, Optional<MandateBounds> mandate) {
        if (criteria.isEmpty()) {
            return null;
        }
        return switch (deciding(criteria)) {
            case AutonomyGate.NO_COVERING_MANDATE -> "This needs your OK because you haven't set up a rule for this.";
            case AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS -> aboveBounds(amount == null ? BigDecimal.ZERO : amount,
                    mandate.orElseThrow());
            case AutonomyGate.NOT_REVERSIBLE -> "This needs your OK because it can't be undone free of charge within a"
                    + " day.";
            case AutonomyGate.INVESTMENT_ABOVE_LIQUID_SHARE -> "This needs your OK because it's a large amount compared"
                    + " with your savings.";
            case AutonomyGate.SUITABILITY_INCOMPLETE -> "I can only suggest this, not do it, until we've gone through a"
                    + " few questions about you.";
            case AutonomyGate.INSURANCE_CLASS -> "For insurance, I can only help you check what you need. I don't"
                    + " change contracts.";
            case AutonomyGate.DATA_CONFIDENCE_LOW -> "I can only suggest this, because some of your recent account data"
                    + " is missing.";
            case AutonomyGate.LEGAL_CLASS -> "I can prepare the letter for you, but you decide whether to send it. Fine"
                    + " never sends letters.";
            case AutonomyGate.NOT_IN_L3_SET -> "This needs your OK because Fine doesn't make this kind of transfer on"
                    + " its own.";
            case RULE_PARAMETER_MISSING -> "I can't make this transfer right now because some information I need for it"
                    + " is missing.";
            case ACCOUNT_MISSING -> "I can't make this transfer because one of its accounts is no longer available.";
            case SUPERSEDED -> "I can't make this transfer because a newer suggestion of the same kind replaces it.";
            default -> throw new IllegalArgumentException("no reason text for " + deciding(criteria));
        };
    }

    /** The reason when the source account no longer holds the amount at confirmation. */
    public static String notCovering(String accountName, BigDecimal balance) {
        return "I can't make this transfer because your " + lowerFirst(accountName) + " holds only " + eur(balance)
                + " now.";
    }

    /** The reason when the emergency fund needs only {@code gap} (zero or less: nothing) to reach its target. */
    public static String topUpNoLongerNeeded(BigDecimal gap, BigDecimal target) {
        if (gap.signum() <= 0) {
            return "I can't make this transfer because your emergency fund has already reached its target of "
                    + eur(target) + ".";
        }
        return "I can't make this transfer because your emergency fund needs only " + eur(gap)
                + " more to reach its target of " + eur(target) + ".";
    }

    /** The fixed "Why?" explanation of the deciding criterion, or {@code null} without criteria. */
    public static String why(List<String> criteria) {
        if (criteria.isEmpty()) {
            return null;
        }
        return switch (deciding(criteria)) {
            case AutonomyGate.NO_COVERING_MANDATE -> "Fine moves money on its own only within a rule you have set up."
                    + " Without such a rule, every transfer waits for your OK.";
            case AutonomyGate.AMOUNT_ABOVE_MANDATE_BOUNDS -> "Your rule sets how much Fine may move per transfer and"
                    + " how much must stay in your overnight deposit. This transfer goes beyond that.";
            case AutonomyGate.NOT_REVERSIBLE -> "Fine acts on its own only where a step can be undone free of charge"
                    + " within a day.";
            case AutonomyGate.INVESTMENT_ABOVE_LIQUID_SHARE -> "Fine asks before any investment that is large compared"
                    + " with the money in your current account and overnight deposit.";
            case AutonomyGate.SUITABILITY_INCOMPLETE -> "Before an investment can be more than a suggestion, Fine needs"
                    + " to know your goals, your experience and how much risk you can take.";
            case AutonomyGate.INSURANCE_CLASS -> "Changing an insurance contract needs a careful look at your situation."
                    + " Fine helps you check, and the decision stays with you.";
            case AutonomyGate.DATA_CONFIDENCE_LOW -> "Some of your recent bookings are missing or not sorted into"
                    + " categories, so Fine can't be sure enough to act.";
            case AutonomyGate.LEGAL_CLASS -> "Letters to others can have legal effects. You stay the one who sends"
                    + " them.";
            case AutonomyGate.NOT_IN_L3_SET -> "Fine makes only a few kinds of transfers on its own, such as covering a"
                    + " shortfall or topping up your emergency fund.";
            case RULE_PARAMETER_MISSING -> "Fine checks every transfer against current rules, for example how large"
                    + " your emergency fund should be. One of them isn't available for this date.";
            case SOURCE_NOT_COVERING -> "Fine checks the balance again right before every transfer and never moves"
                    + " more than the account holds.";
            case ACCOUNT_MISSING -> "Fine checks both accounts again right before every transfer.";
            case SUPERSEDED -> "Fine checks right before every transfer whether it is still needed. Only the newest"
                    + " suggestion of a kind can be confirmed, so money never moves twice for the same need.";
            case NO_LONGER_NEEDED -> "Fine checks right before every transfer whether it is still needed and never"
                    + " tops up your emergency fund beyond its target.";
            default -> throw new IllegalArgumentException("no explanation for " + deciding(criteria));
        };
    }

    /**
     * The criterion that decided the level: the first (in table order) of those with the lowest cap; a failed check
     * that is no gate criterion decides before any gate criterion.
     */
    static String deciding(List<String> criteria) {
        return criteria.stream()
                .min(Comparator.comparing((String c) -> AutonomyGate.cap(c).orElse(null),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow();
    }

    /** An amount as in confirmations: {@code 1,240.00 EUR}, a real minus sign for negatives. */
    public static String eur(BigDecimal amount) {
        BigDecimal cents = amount.setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return (cents.signum() < 0 ? String.valueOf(MINUS) : "") + format.format(cents.abs()) + " EUR";
    }

    private static String aboveBounds(BigDecimal amount, MandateBounds bounds) {
        if (amount.compareTo(bounds.maxPerAction()) > 0) {
            return "This needs your OK because " + eur(amount) + " is more than your rule allows ("
                    + eur(bounds.maxPerAction()) + " per transfer).";
        }
        return "This needs your OK because it would take your overnight deposit below the " + eur(bounds.floor())
                + " you set as its minimum.";
    }

    private static String lowerFirst(String name) {
        return name.isEmpty() ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
