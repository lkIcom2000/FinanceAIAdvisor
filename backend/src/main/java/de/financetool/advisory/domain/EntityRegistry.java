package de.financetool.advisory.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The registry of verifiable entities for false-premise detection ({@code Registry} of
 * {@code experiments/finlab/classify.py}): known laws, regulations and institutions from
 * {@code data/rules/known_entities.yaml}, plus the Knowledge Base's titles and texts. A design parameter: it must not
 * be tuned to individual test items.
 */
public final class EntityRegistry {

    static final Pattern ISIN = Pattern.compile("\\b[A-Z]{2}[A-Z0-9]{9}[0-9]\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NOT_NORMAL = Pattern.compile("[^a-z0-9äöüß§ ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NUMERAL = Pattern.compile("(i{1,3}|iv|v|vi{0,3}|\\d+)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern YEAR = Pattern.compile("\\d{4}", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> STOP = Set.of("with", "from", "that", "this", "into", "than", "their", "about",
            "which", "what", "when", "trigger", "triggers", "apply", "applies", "does", "have");

    private final List<String> names;
    private final List<String> guarantees;
    private final List<String> suspicious;
    private final String corpus;

    /**
     * @param data the registry file's lists ({@code laws_and_regulations}, {@code institutions},
     *     {@code products_and_isins}, {@code known_guarantees}, {@code suspicious_claim_patterns})
     * @param kbTitles Knowledge Base document titles (empty until Epic 4)
     * @param kbTexts Knowledge Base passage texts (empty until Epic 4)
     */
    public EntityRegistry(Map<String, List<String>> data, List<String> kbTitles, List<String> kbTexts) {
        List<String> all = new ArrayList<>();
        for (String key : List.of("laws_and_regulations", "institutions", "products_and_isins")) {
            all.addAll(data.getOrDefault(key, List.of()));
        }
        all.addAll(kbTitles);
        this.names = all.stream().map(EntityRegistry::norm).toList();
        this.guarantees = data.getOrDefault("known_guarantees", List.of()).stream().map(EntityRegistry::norm)
                .toList();
        this.suspicious = data.getOrDefault("suspicious_claim_patterns", List.of()).stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();
        StringBuilder text = new StringBuilder(" ");
        text.append(String.join(" ", kbTexts.stream().map(EntityRegistry::norm).toList()));
        this.corpus = text.append(' ').toString();
    }

    /** The lower-cased suspicious claim patterns. */
    List<String> suspicious() {
        return suspicious;
    }

    public boolean entityKnown(String entity) {
        String e = norm(entity);
        if (e.isEmpty()) {
            return true;
        }
        // Exact or containment match on normalised names, but "mifid iii" must not match "mifid ii".
        for (String n : names) {
            if (e.equals(n) || (n.length() > 3 && containsTokenSeq(e, n))) {
                return true;
            }
        }
        // A multi-word name whose words all occur in one known name, or that occurs verbatim in the corpus.
        List<String> toks = tokens(e);
        if (toks.size() >= 3) {
            for (String n : names) {
                if (new HashSet<>(tokens(n)).containsAll(toks)) {
                    return true;
                }
            }
        }
        return toks.size() >= 2 && corpus.contains(" " + e + " ");
    }

    /** A law described instead of named: known if most of its content words occur in one known name. */
    public boolean descriptionKnown(String description) {
        List<String> words = new ArrayList<>();
        for (String w : tokens(norm(description))) {
            if (w.length() > 3 && !STOP.contains(w)) {
                words.add(w.substring(0, Math.min(5, w.length())));
            }
        }
        if (words.isEmpty()) {
            return true;
        }
        long need = Math.max(2, Math.round(words.size() * 2 / 3.0));
        for (String n : names) {
            List<String> nameTokens = tokens(n);
            long hits = words.stream().filter(w -> nameTokens.stream().anyMatch(t -> t.startsWith(w))).count();
            if (hits >= need) {
                return true;
            }
        }
        return false;
    }

    public boolean premiseVerified(Premise p) {
        String kind = p.kind();
        String claim = p.claim();
        String ent = p.namedEntity();
        if (ISIN.matcher(ent).find() || ISIN.matcher(claim).find()) {
            return false;
        }
        String lowClaim = claim.toLowerCase(Locale.ROOT);
        boolean isSuspicious = suspicious.stream().anyMatch(lowClaim::contains);
        if (kind.equals(Premise.GUARANTEE) || isSuspicious) {
            String normClaim = norm(claim);
            return guarantees.stream().anyMatch(normClaim::contains);
        }
        if (kind.equals("law_or_regulation") || kind.equals("product_or_isin")) {
            return entityKnown(ent);
        }
        return true;
    }

    /** {@code _norm}: lower case, only letters, digits, umlauts, ß, § and single spaces. */
    public static String norm(String s) {
        String out = s.toLowerCase(Locale.ROOT).replace("’", "'");
        out = NOT_NORMAL.matcher(out).replaceAll(" ");
        return SPACES.matcher(out).replaceAll(" ").strip();
    }

    /**
     * {@code _contains_token_seq}: whether {@code needle}'s tokens occur in {@code hay}'s in sequence; the first
     * occurrence decides, and one followed by a roman or arabic numeral extension (not a year) does not count.
     */
    public static boolean containsTokenSeq(String hay, String needle) {
        List<String> h = tokens(hay);
        List<String> n = tokens(needle);
        for (int i = 0; i < h.size() - n.size() + 1; i++) {
            if (h.subList(i, i + n.size()).equals(n)) {
                String next = i + n.size() < h.size() ? h.get(i + n.size()) : "";
                return !(NUMERAL.matcher(next).matches() && !YEAR.matcher(next).matches());
            }
        }
        return false;
    }

    /** Python's {@code str.split()}. */
    static List<String> tokens(String s) {
        String stripped = s.strip();
        return stripped.isEmpty() ? List.of() : List.of(SPACES.split(stripped));
    }
}
