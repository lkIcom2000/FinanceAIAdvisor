package de.financetool.advisory.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The number parsing of {@code experiments/finlab/numbercheck.py} ({@code parse_numbers}, {@code _to_decimal},
 * {@code numbers_in}), ported verbatim. Dates, years, citation markers, ISINs, list positions and reference numbers
 * (article, paragraph, ...) are masked; English (1,000.50) and German (1.000,50) formats are both parsed. {@link NumberCheck}
 * matches the numbers against the turn's {@link NumberLedger}.
 */
public final class NumberParser {

    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;
    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    static final Pattern NUM = Pattern.compile("(?<![\\w.,])"
            + "(?<sign>[-\u2212\u2013]?)"
            + "(?<num>\\d{1,3}(?:[.,\u202F ]\\d{3})+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?)"
            // German suffixes (Story 13.1): "millionen" ahead of "million", so the whole word is matched
            // the German words end at a word boundary ("5 Prozentpunkte" is no percentage)
            + "(?<suffix>\\s?(?:%|percent|per cent|k\\b|thousand|millionen(?!\\p{L})|million|mn\\b|bn\\b|billion"
            + "|prozent(?!\\p{L})|tausend(?!\\p{L})|milliarden(?!\\p{L})|milliarde(?!\\p{L})|tsd\\.|mio\\.|mrd\\."
            + "|tsd\\b|mio\\b|mrd\\b))?", CI);
    static final Pattern APPROX = Pattern.compile(
            "\\b(about|around|roughly|approximately|approx\\.?|circa|ca\\.|nearly|almost|some|up to|~|"
                    + "etwa|rund|ungefähr|knapp|fast|bis zu)\\s*$", CI);
    static final Pattern REF_BEFORE = Pattern.compile(
            "(article|art\\.|\u00A7|section|paragraph|para\\.|annex|recital|level|step|item|question|tier|class|"
                    + "pillar|chapter|no\\.|nr\\.|number|version|mifid|ucits|basel|solvency|psd|ifrs|figure|table|"
                    + "absatz|abs\\.|ziffer|buchstabe|anhang|kapitel|stufe|schritt|frage|abbildung|tabelle)\\s*$",
            CI);
    /** English and German month names and abbreviations ("März", "Mai", "Juni", "Okt.", "Dez."). */
    private static final String MONTHS = "((jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"
            + "|märz|mai\\b|okt[a-z]*|dez[a-z]*)\\.?";
    static final List<Pattern> DATES = List.of(
            Pattern.compile("\\b\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}\\b", U),
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b", U),
            // "3. März 2026": the German day carries a dot (Story 13.1)
            Pattern.compile("\\b\\d{1,2}(st|nd|rd|th|\\.)?\\s+" + MONTHS + "(\\s+\\d{4})?\\b", CI),
            Pattern.compile("\\b" + MONTHS + "\\s+\\d{1,2}(st|nd|rd|th)?(,?\\s+\\d{4})?\\b", CI),
            Pattern.compile("\\b(19|20)\\d{2}\\b", U),
            Pattern.compile("\\[S\\d+\\]", U),
            Pattern.compile("\\b[A-Z]{2}[A-Z0-9]{9}[0-9]\\b", U),
            Pattern.compile("\\bL[0-4]\\b|\\bE[1-4]\\b|\\bSM-\\d+\\b|\\bFR-\\d+\\b", U));
    static final Pattern LIST_POS = Pattern.compile("^\\s*(\\d{1,2})[.)]\\s", Pattern.MULTILINE | U);
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b", U);
    private static final Pattern THOUSANDS_COMMA = Pattern.compile("\\d{1,3}(,\\d{3})+", U);
    private static final Pattern THOUSANDS_DOT = Pattern.compile("\\d{1,3}(\\.\\d{3})+", U);
    private static final Map<String, BigDecimal> MULTIPLIERS = Map.of("k", BigDecimal.valueOf(1000), "thousand",
            BigDecimal.valueOf(1000), "million", BigDecimal.TEN.pow(6), "mn", BigDecimal.TEN.pow(6), "bn",
            BigDecimal.TEN.pow(9), "billion", BigDecimal.TEN.pow(9));
    private static final Map<String, BigDecimal> MULTIPLIERS_DE = Map.of("tausend", BigDecimal.valueOf(1000), "tsd",
            BigDecimal.valueOf(1000), "millionen", BigDecimal.TEN.pow(6), "mio", BigDecimal.TEN.pow(6), "milliarden",
            BigDecimal.TEN.pow(9), "milliarde", BigDecimal.TEN.pow(9), "mrd", BigDecimal.TEN.pow(9));

    /**
     * One number found in a text ({@code FoundNumber}).
     *
     * @param text the matched text, stripped
     * @param decimals the decimals shown
     * @param start where the match starts in the text
     */
    public record Found(String text, BigDecimal value, boolean percent, boolean approximate, int decimals,
            int start) {
    }

    private NumberParser() {
    }

    /** The numbers of {@code text} outside dates, list positions and references ({@code parse_numbers}). */
    public static List<Found> parse(String text) {
        String masked = text;
        for (Pattern date : DATES) {
            masked = date.matcher(masked).replaceAll(m -> " ".repeat(m.group().length()));
        }
        masked = LIST_POS.matcher(masked).replaceAll(
                m -> Matcher.quoteReplacement(m.group().replace(m.group(1), " ".repeat(m.group(1).length()))));
        List<Found> out = new ArrayList<>();
        Matcher m = NUM.matcher(masked);
        while (m.find()) {
            String before = masked.substring(Math.max(0, m.start() - 14), m.start());
            if (REF_BEFORE.matcher(before).find()) {
                continue;
            }
            Parsed parsed = toDecimal(m.group("num"));
            if (parsed == null) {
                continue;
            }
            String suffix = m.group("suffix") == null ? "" : m.group("suffix").strip().toLowerCase(java.util.Locale.ROOT);
            String unit = suffix.endsWith(".") ? suffix.substring(0, suffix.length() - 1) : suffix;
            BigDecimal value = parsed.value().multiply(MULTIPLIERS.getOrDefault(unit,
                    MULTIPLIERS_DE.getOrDefault(unit, BigDecimal.ONE)));
            if (!m.group("sign").isEmpty()) {
                value = value.negate();
            }
            boolean percent = suffix.equals("%") || suffix.equals("percent") || suffix.equals("per cent")
                    || suffix.equals("prozent");
            boolean approximate = APPROX.matcher(text.substring(Math.max(0, m.start() - 16), m.start())).find();
            out.add(new Found(m.group().strip(), value, percent, approximate, parsed.decimals(), m.start()));
        }
        return out;
    }

    /**
     * The numbers in the user's own message, which count as sourced (FR-12): each number, a percentage also as a
     * fraction, and every year ({@code numbers_in}).
     */
    public static List<BigDecimal> numbersIn(String text) {
        List<BigDecimal> values = new ArrayList<>();
        for (Found found : parse(text)) {
            values.add(found.value());
            if (found.percent()) {
                values.add(found.value().movePointLeft(2));
            }
        }
        Matcher years = YEAR.matcher(text);
        while (years.find()) {
            values.add(new BigDecimal(years.group()));
        }
        return values;
    }

    private record Parsed(BigDecimal value, int decimals) {
    }

    /** {@code _to_decimal}: "1,000" is thousands, "2,5" a German decimal. */
    private static Parsed toDecimal(String number) {
        String s = number.replace("\u202F", "").replace(" ", "");
        char decimalSeparator;
        if (s.contains(",") && s.contains(".")) {
            decimalSeparator = s.lastIndexOf(',') > s.lastIndexOf('.') ? ',' : '.';
        } else if (s.contains(",")) {
            decimalSeparator = THOUSANDS_COMMA.matcher(s).matches() ? '.' : ',';
        } else if (s.contains(".")) {
            decimalSeparator = THOUSANDS_DOT.matcher(s).matches() ? ',' : '.';
        } else {
            decimalSeparator = '.';
        }
        char thousands = decimalSeparator == '.' ? ',' : '.';
        s = s.replace(String.valueOf(thousands), "").replace(decimalSeparator, '.');
        int dot = s.indexOf('.');
        int decimals = dot >= 0 ? s.split("\\.", -1)[1].length() : 0;
        try {
            return new Parsed(new BigDecimal(s), decimals);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
