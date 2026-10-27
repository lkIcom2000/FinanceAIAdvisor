package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.modulith.NamedInterface;

/**
 * The customer texts of a rule (UX-DR22, UX-DR23, UX-DR36): the plain-language summary, what Fine will and will not do,
 * how to undo and stop, and the limit checks. Customer text says "rule", never "Mandate". Pure.
 */
@NamedInterface("types")
public final class RuleTexts {

    /** The largest limit a rule accepts. */
    static final BigDecimal LARGEST = new BigDecimal("1000000");

    public static final String UNDO_AND_STOP = "You can undo each transfer with one tap until 5 pm on the next"
            + " business day. You can stop this rule at any time with \"Revoke\" on its card under \"Your rules\" on"
            + " the Home tab.";

    private RuleTexts() {
    }

    /** Why these limits can't be used, in customer words; empty if they can. */
    public static Optional<String> problem(RuleBounds bounds) {
        if (bounds.maxPerAction().signum() <= 0 || bounds.maxPerMonth().signum() <= 0) {
            return Optional.of("Each limit must be more than 0 EUR.");
        }
        if (bounds.floor().signum() < 0) {
            return Optional.of("The minimum balance can't be below 0 EUR.");
        }
        if (moreThanCents(bounds.maxPerAction()) || moreThanCents(bounds.maxPerMonth())
                || moreThanCents(bounds.floor())) {
            return Optional.of("Please use whole cents at most.");
        }
        if (bounds.maxPerAction().compareTo(LARGEST) > 0 || bounds.maxPerMonth().compareTo(LARGEST) > 0
                || bounds.floor().compareTo(LARGEST) > 0) {
            return Optional.of("Each amount must be at most " + eur(LARGEST) + ".");
        }
        if (bounds.maxPerMonth().compareTo(bounds.maxPerAction()) < 0) {
            return Optional.of("The most per month can't be less than the most per transfer.");
        }
        return Optional.empty();
    }

    /** The rule card title and the first line of the check step. */
    public static String summary(RuleTemplate template, RuleBounds bounds) {
        return switch (template) {
            case SAFETY_CUSHION -> "Keep my current account above 0 EUR, using my overnight deposit, but never let the"
                    + " overnight deposit fall below " + eur(bounds.floor());
            case EMERGENCY_FUND_TOP_UP -> "Top up my emergency fund in my overnight deposit from my current account,"
                    + " but never let the current account fall below " + eur(bounds.floor());
        };
    }

    /** What Fine will do under the rule. */
    public static List<String> will(RuleTemplate template, RuleBounds bounds) {
        String when = switch (template) {
            case SAFETY_CUSHION -> "Move money from your overnight deposit to your current account when your current"
                    + " account is forecast to drop below 0 EUR.";
            case EMERGENCY_FUND_TOP_UP -> "Move money from your current account to your overnight deposit when your"
                    + " emergency fund is below its target.";
        };
        return List.of(when, "Move at most " + eur(bounds.maxPerAction()) + " per transfer and "
                + eur(bounds.maxPerMonth()) + " per month.", "Tell you right away about every transfer it makes.");
    }

    /** What Fine will not do under the rule. */
    public static List<String> wont(RuleTemplate template, RuleBounds bounds) {
        String account = template.fromOvernight() ? "overnight deposit" : "current account";
        return List.of("Take your " + account + " below " + eur(bounds.floor()) + ".",
                "Move money to any other account or to anyone else.",
                "Invest your money or change your contracts.");
    }

    /** Where the suggested limits come from, shown with them in the setup step. */
    public static String source(RuleTemplate template) {
        return switch (template) {
            case SAFETY_CUSHION -> "Suggested by Fine from your lowest forecast balance in the next three months and"
                    + " your emergency fund target.";
            case EMERGENCY_FUND_TOP_UP -> "Suggested by Fine from the gap to your emergency fund target and what an"
                    + " average month leaves over.";
        };
    }

    private static boolean moreThanCents(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() > 2;
    }

    /** An amount in text format: {@code 2,000 EUR}, cents only when there are any. */
    static String eur(BigDecimal amount) {
        BigDecimal cents = amount.setScale(2, RoundingMode.HALF_UP);
        String pattern = cents.stripTrailingZeros().scale() <= 0 ? "#,##0" : "#,##0.00";
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(cents) + " EUR";
    }
}
