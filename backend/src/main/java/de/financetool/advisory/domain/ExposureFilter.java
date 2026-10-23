package de.financetool.advisory.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Data Exposure Levels for the phrasing role (FR-43, AD-5), mirroring the Experiment Track's
 * {@code experiments/finlab/exposure.py} so the app runs what E4 measures. Only {@code AGGREGATED} and {@code NONE}
 * exist ({@code raw}/{@code pseudonymised} only if E4 selects one). Core results are tool output, not persona
 * attributes: they pass in full at both levels.
 */
public final class ExposureFilter {

    /** The levels of {@code DataExposureLevel}, as the domain sees them. */
    public enum Level {
        AGGREGATED,
        NONE
    }

    /** The message at level {@code NONE} while no depersonalised question exists (as {@code exposure.py}). */
    public static final String WITHHELD = "[question withheld: no personal data may be shared at this exposure level]";

    /** How many released turns the history holds at most. */
    public static final int HISTORY_TURNS = 3;

    /**
     * Matches IBANs in any case, unseparated or with single spaces or hyphens between the groups, including the
     * two-character tail of a German IBAN (deliberately not {@code exposure.py}'s pattern, which misses that tail).
     */
    static final Pattern IBAN = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?:[ -]?[A-Z0-9]{4}){2,7}(?:[ -]?[A-Z0-9]{1,4})?\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.]+\\b");
    /** A title followed by a capitalised name (Unicode letters, hyphens) and up to two more capitalised words. */
    static final Pattern CLIENT = Pattern.compile("\\b(Client|Customer|Mr\\.?|Mrs\\.?|Ms\\.?|Herr|Frau|Kunde|Kundin)"
            + "(?:\\s+\\p{Lu}[\\p{L}'’-]*){1,3}");
    private static final String NUMBER = "(?<![\\d.,])(?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)(?:[.,]\\d+)?";
    /**
     * A money amount with the currency after ("12,400 EUR") or before it ("EUR 12,400", "€12.400"). {@code euros?}
     * comes before {@code EUR}: in {@code exposure.py}'s order "Euro" would leave an "o" behind.
     */
    static final Pattern MONEY = Pattern.compile(
            "(?:\\bEUR|€)\\s?" + NUMBER + "|" + NUMBER + "\\s?(?:euros?|EUR|€)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Two digits that are a whole number, not part of a longer one ("12,400" holds no age). */
    private static final String TWO_DIGITS = "(?<![\\d.,])(\\d{2})(?!\\d|[.,]\\d)";
    /** An age after "age", "aged", "I am", "I'm" or German "ich bin". */
    static final Pattern AGE = Pattern.compile("\\b(?:age|aged|I am|I['’]m|ich bin)\\s*:?\\s*" + TWO_DIGITS,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** An age before "Jahre" ("34 Jahre alt") or "years old". */
    static final Pattern AGE_BEFORE = Pattern.compile("\\b" + TWO_DIGITS + "(?=\\s*(?:Jahre\\b|years?\\s+old\\b))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /** Shown instead of an amount that cannot be banded (a digit run too long for a double). */
    public static final String UNREADABLE_AMOUNT = "[AMOUNT]";
    private static final Pattern THOUSANDS_ONLY = Pattern.compile("\\d{1,3}([.,]\\d{3})+");

    /** Profile numbers that are no money amount and stay as they are at {@code AGGREGATED}. */
    static final Set<String> KEPT_PROFILE_KEYS = Set.of("investment_horizon_years", "children");

    private ExposureFilter() {
    }

    /**
     * The payload for the phrasing role at {@code level}.
     *
     * @param depersonalisedQuestion the NLU's depersonalised question, {@code null} while none exists
     * @param history the last released turns, oldest first
     */
    public static PromptPayload apply(Level level, String message, String depersonalisedQuestion,
            List<PromptPayload.HistoryTurn> history, Map<String, Object> profile, String toolResults,
            List<PromptPayload.Passage> passages) {
        List<PromptPayload.HistoryTurn> shownHistory = level == Level.NONE ? List.of()
                : history.stream().map(turn -> new PromptPayload.HistoryTurn(filterMessage(turn.userText(), level,
                        null), filterMessage(turn.agentText(), level, null))).toList();
        boolean withheld = level == Level.NONE
                && (depersonalisedQuestion == null || depersonalisedQuestion.isBlank());
        return new PromptPayload(filterMessage(message, level, depersonalisedQuestion), withheld, shownHistory,
                filterProfile(profile, level), toolResults, passages);
    }

    /** A message (or history text) as the model may see it at {@code level}. */
    public static String filterMessage(String message, Level level, String depersonalisedQuestion) {
        if (level == Level.NONE) {
            return depersonalisedQuestion == null || depersonalisedQuestion.isBlank() ? WITHHELD
                    : depersonalisedQuestion;
        }
        String out = EMAIL.matcher(message).replaceAll("[EMAIL]");
        out = IBAN.matcher(out).replaceAll("[IBAN]");
        out = CLIENT.matcher(out).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + " X"));
        // Ages before amounts, so that the age patterns never see a band.
        out = AGE.matcher(out).replaceAll(ExposureFilter::decadeOfMatch);
        out = AGE_BEFORE.matcher(out).replaceAll(ExposureFilter::decadeOfMatch);
        out = MONEY.matcher(out).replaceAll(m -> Matcher.quoteReplacement(band(amount(m.group()))));
        return out;
    }

    /** The match with its age group replaced by the decade. */
    private static String decadeOfMatch(java.util.regex.MatchResult m) {
        String text = m.group();
        int at = m.start(1) - m.start();
        return Matcher.quoteReplacement(text.substring(0, at) + decade(Integer.parseInt(m.group(1)))
                + text.substring(at + 2));
    }

    /**
     * The profile context at {@code level}: empty at {@code NONE}; at aggregated numbers become bands or decades
     * and texts go through the message filter.
     */
    public static Map<String, Object> filterProfile(Map<String, Object> profile, Level level) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (level == Level.NONE) {
            return out;
        }
        profile.forEach((key, value) -> {
            if (value instanceof Number number && !KEPT_PROFILE_KEYS.contains(key)) {
                out.put(key, key.equals("age") ? decade(number.intValue()) : band(number.doubleValue()));
            } else if (value instanceof String text) {
                out.put(key, filterMessage(text, level, null));
            } else {
                out.put(key, value);
            }
        });
        return out;
    }

    /**
     * The money band of {@code value} ({@code band()} of {@code exposure.py}): step = max(100, 10^floor(log10 v)/2),
     * shown as "lo–hi EUR"; 0 or less is "0 EUR"; a non-finite value is {@link #UNREADABLE_AMOUNT}.
     */
    public static String band(double value) {
        if (!Double.isFinite(value)) {
            return UNREADABLE_AMOUNT;
        }
        if (value <= 0) {
            return "0 EUR";
        }
        int exponent = (int) Math.log10(value);
        double step = Math.max(100, Math.pow(10, exponent) / 2);
        double low = Math.floor(value / step) * step;
        return String.format(Locale.US, "%,.0f–%,.0f EUR", low, low + step);
    }

    static String decade(int age) {
        return (age / 10 * 10) + "s";
    }

    /** The amount of a money match in English ("12,400.50") or German ("12.400,50") format. */
    static double amount(String match) {
        String digits = match.replaceAll("[^\\d.,]", "");
        if (THOUSANDS_ONLY.matcher(digits).matches()) {
            return Double.parseDouble(digits.replaceAll("[.,]", ""));
        }
        int lastDot = digits.lastIndexOf('.');
        int lastComma = digits.lastIndexOf(',');
        int decimal = Math.max(lastDot, lastComma);
        if (decimal < 0) {
            return Double.parseDouble(digits);
        }
        String whole = digits.substring(0, decimal).replaceAll("[.,]", "");
        return Double.parseDouble((whole.isEmpty() ? "0" : whole) + "." + digits.substring(decimal + 1));
    }
}
