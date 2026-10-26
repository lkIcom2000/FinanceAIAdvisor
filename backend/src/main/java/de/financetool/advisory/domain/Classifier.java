package de.financetool.advisory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic classification of a message (FR-9, AD-4): a pure port of {@code experiments/finlab/classify.py}
 * (commit {@code b72a70e}). The rules run in a fixed order: trap, out of scope, compute, general, personal (profile
 * check). No model is called; the same input always gives the same class.
 */
public final class Classifier {

    /**
     * Legal assessment of letters or contracts: an app-only category (not in the NLU schema), added from the message
     * wording by {@link NluExtraction#ground(String)}.
     */
    public static final String LEGAL_ASSESSMENT = "legal_assessment";
    public static final Set<String> OUT_OF_SCOPE_CATEGORIES = Set.of("single_stock", "penny_stock",
            "derivative_or_certificate", "crypto", "forex_or_cfd", "building_society_savings",
            "insurance_tariff_selection", "individual_tax_advice", "single_bond", LEGAL_ASSESSMENT);
    static final Set<String> PERSONAL_TYPES = Set.of("personal_recommendation", "product_selection",
            "profile_or_eligibility_question");

    private static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    static final Pattern SELF_CLAIM = Pattern.compile(
            "(the )?(user|client|customer)\\b|(i|i'm|i've|my|we|our|you|your)\\b", CI);
    static final Pattern DESCRIBED_LAW = Pattern.compile("\\bthe ((?:EU|European|German|new) )?"
            + "(directive|regulation|law|act)\\s+(?:on|for|about|regarding|requiring)\\s+([^?.,;]+)", CI);
    static final Pattern QUESTION_START = Pattern.compile(
            "\\s*(do|does|did|is|are|was|were|can|could|will|would|has|have)\\b", CI);
    private static final Pattern NAME = Pattern.compile(".[A-Z]|\\b[A-Z]{2,}\\b|\\b(19|20)\\d{2}\\b|§",
            Pattern.UNICODE_CHARACTER_CLASS);

    private Classifier() {
    }

    /**
     * @param nlu the grounded NLU extraction
     * @param storedProfile the persona's stored profile
     * @param computable whether the tool plan has the parameters for a calculation (false until Story 3.7)
     * @param message the user's message
     */
    public static Classification classify(NluExtraction nlu, Map<String, Object> storedProfile,
            ProfileCompleteness completeness, EntityRegistry registry, boolean computable, String message) {
        return classify(nlu, storedProfile, completeness, registry, computable, message, false);
    }

    /**
     * @param strict the strict FR-17 rule of a persona workspace: a recommendation needs the stored profile alone to
     *     meet the topic minimum and hold every strict field; the stated facts do not count, and the missing strict
     *     fields join the missing fields of {@code ASK_PROFILE}
     */
    public static Classification classify(NluExtraction nlu, Map<String, Object> storedProfile,
            ProfileCompleteness completeness, EntityRegistry registry, boolean computable, String message,
            boolean strict) {
        List<String> reasons = new ArrayList<>();
        String topic = nlu.topic();
        if (nlu.instructionOverrideAttempt()) {
            reasons.add("instruction override attempt treated as data (FR-45)");
        }

        List<Premise> unverified = new ArrayList<>();
        for (Premise p : premisesToCheck(nlu, message, registry)) {
            boolean verified = p.kind().equals(Premise.DESCRIBED_LAW) ? registry.descriptionKnown(p.description())
                    : registry.premiseVerified(p);
            if (!verified) {
                unverified.add(p);
            }
        }
        if (!unverified.isEmpty()) {
            reasons.add("question presupposes unverifiable premise(s)");
            return new Classification(MessageClass.DETECT_TRAP, reasons, List.of(), unverified, List.of(), topic);
        }

        List<String> oos = new ArrayList<>(new TreeSet<>(nlu.productCategories().stream()
                .filter(OUT_OF_SCOPE_CATEGORIES::contains).toList()));
        String type = nlu.requestType();
        if (!oos.isEmpty() && (PERSONAL_TYPES.contains(type) || type.equals("computation"))) {
            reasons.add("personal request on out-of-scope product(s): " + String.join(", ", oos));
            return new Classification(MessageClass.REFUSE_OUT_OF_SCOPE, reasons, List.of(), List.of(), oos, topic);
        }

        if (computable && (type.equals("computation") || type.equals("profile_or_eligibility_question"))) {
            reasons.add("computation with sufficient parameters");
            return new Classification(MessageClass.COMPUTE, reasons, List.of(), List.of(), List.of(), topic);
        }

        if (type.equals("general_explanation") || nlu.subject().equals("generic")) {
            reasons.add("general explanation");
            return new Classification(MessageClass.INFORM, reasons, List.of(), List.of(), List.of(), topic);
        }

        if (PERSONAL_TYPES.contains(type) || type.equals("computation")) {
            if (strict) {
                ProfileCompleteness.Result result = completeness.check(storedProfile, Map.of(), topic);
                List<String> strictMissing = completeness.strictMissing(storedProfile, topic);
                if (result.complete() && strictMissing.isEmpty()) {
                    reasons.add("confirmed profile complete for topic '" + topic + "' (strict FR-17)");
                    return new Classification(MessageClass.RECOMMEND_CATEGORY, reasons, List.of(), List.of(),
                            List.of(), topic);
                }
                reasons.add("confirmed profile incomplete for topic '" + topic + "' (strict FR-17)");
                List<String> missing = new ArrayList<>(result.missingFields());
                strictMissing.stream().filter(f -> !missing.contains(f)).forEach(missing::add);
                return new Classification(MessageClass.ASK_PROFILE, reasons, missing, List.of(), List.of(), topic);
            }
            ProfileCompleteness.Result result = completeness.check(storedProfile, nlu.statedProfile(), topic);
            if (result.complete()) {
                reasons.add("profile minimum met for topic '" + topic + "'");
                if (!result.limitations().isEmpty()) {
                    reasons.add("not known yet (limitations): " + String.join(", ", result.limitations()));
                }
                return new Classification(MessageClass.RECOMMEND_CATEGORY, reasons, List.of(), List.of(), List.of(),
                        topic, result.limitations());
            }
            reasons.add("profile minimum not met for topic '" + topic + "'");
            return new Classification(MessageClass.ASK_PROFILE, reasons, result.missingFields(), List.of(), List.of(),
                    topic);
        }

        reasons.add("default: general information");
        return new Classification(MessageClass.INFORM, reasons, List.of(), List.of(), List.of(), topic);
    }

    /**
     * {@code premises_to_check}: only claims about external, named things are checked. Facts the user states about
     * their own situation are profile data, and generic terms are not verifiable names.
     */
    public static List<Premise> premisesToCheck(NluExtraction nlu, String message, EntityRegistry registry) {
        List<Premise> out = new ArrayList<>();
        String normMessage = EntityRegistry.norm(message);
        for (Premise p : nlu.assertedPremises()) {
            String claim = p.claim().strip();
            String ent = p.namedEntity().strip();
            if (SELF_CLAIM.matcher(claim).lookingAt()) {
                continue;
            }
            String normEnt = EntityRegistry.norm(ent);
            if (!ent.isEmpty() && !(!normEnt.isEmpty() && EntityRegistry.containsTokenSeq(normMessage, normEnt)
                    && isName(ent))) {
                continue;
            }
            if (ent.isEmpty() && !p.kind().equals(Premise.GUARANTEE)) {
                continue;
            }
            out.add(p);
        }
        Matcher m = DESCRIBED_LAW.matcher(message);
        while (m.find()) {
            out.add(new Premise(m.group(0), Premise.DESCRIBED_LAW, "", m.group(3)));
        }
        // A guarantee or compensation stated as a fact about an external body or law is checked against the known
        // guarantees, whatever kind the NLU gave it.
        String low = message.toLowerCase(Locale.ROOT);
        if (!out.isEmpty() && !QUESTION_START.matcher(message).lookingAt()
                && registry.suspicious().stream().anyMatch(low::contains)) {
            out.add(new Premise(message, Premise.GUARANTEE, ""));
        }
        return out;
    }

    /**
     * {@code _is_name}: a proper name, not a generic term: a capital after the first letter, an acronym, a year, a
     * section sign or an ISIN.
     */
    static boolean isName(String entity) {
        return NAME.matcher(entity).find() || EntityRegistry.ISIN.matcher(entity).find();
    }
}
