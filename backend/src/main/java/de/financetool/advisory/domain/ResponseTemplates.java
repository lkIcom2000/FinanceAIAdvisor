package de.financetool.advisory.domain;

import java.util.List;

/**
 * The fixed, pre-approved answers block routing releases instead of a draft: {@code TEMPLATES} of
 * {@code experiments/finlab/prompts.py}, verbatim, except {@code ESCALATE}: the handover card's text (UX-DR26,
 * Story 3.5).
 */
public final class ResponseTemplates {

    public static final String ESCALATE = "Your question has been passed to a human advisor at your bank. You'll see "
            + "their answer here in the chat.";

    static final String REFUSE_OUT_OF_SCOPE = "This is outside what I can advise on: {oos} are not part of this "
            + "service, because they carry risks that don't fit a basic advisory service for everyday savers. What I "
            + "can do is look at your emergency fund, your savings goals and a diversified, low-cost ETF savings plan "
            + "with you.";

    /** Why an {@code ASK_PROFILE} answer asks (Story 6.1): every such answer starts with it. */
    public static final String WHY_ASK_PROFILE = "Before I can say whether this fits you, I need to know a bit more "
            + "about your situation. It takes about three minutes.";

    static final String ASK_PROFILE = WHY_ASK_PROFILE + " I need: {missing}. Could you tell me these? Then I'll tell "
            + "you what fits your situation and why.";

    /** The disclosed limitations of a recommendation on a profile with strict FR-17 fields still unknown (AD-23). */
    static final String LIMITATIONS = "This is based only on what I know so far. Not known yet: {fields}.";

    /** The default missing-fields text of {@code _route}. */
    static final String DEFAULT_MISSING = "your goals, horizon, income and expenses, emergency fund and risk tolerance";

    /** The text of a parameter proposal card (AD-24): no figures, the card's rows carry them. */
    public static final String PARAMETER_PROPOSAL = "Here's what I understood from your question. Please check the "
            + "values below, then choose Calculate, or change them first.";

    public static final String NO_VALID_SOURCE = NoValidSource.TEXT;

    /** The in-scope next steps of every released refusal (UX-DR11), as suggestion chips. */
    public static final List<String> REFUSAL_SUGGESTIONS = List.of("Look at my emergency fund",
            "Help me set a savings goal");

    /** The callout of a trap whose only unverified premise is a guarantee without a name. */
    static final String GUARANTEE_CALLOUT = "I couldn't confirm the guarantee your question assumes.";
    /** The callout when no premise can be named in the message's own words. */
    static final String ASSUMPTION_CALLOUT = "I couldn't confirm something your question assumes.";

    private ResponseTemplates() {
    }

    /**
     * The premise callout of a {@code DETECT_TRAP} card: one sentence naming the unverified premises in the message's
     * own words (named entities, described laws that occur in the message), never details about them.
     *
     * @param message the user's message; a name the NLU proposed that is not in it is not quoted
     */
    public static String premiseCallout(List<Premise> unverified, String message) {
        String low = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        List<String> parts = new java.util.ArrayList<>();
        for (Premise p : unverified) {
            String words = p.kind().equals(Premise.DESCRIBED_LAW)
                    ? (p.description() == null ? "" : p.description().strip()) : p.namedEntity().strip();
            if (words.isEmpty() || !low.contains(words.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            String part = p.kind().equals(Premise.DESCRIBED_LAW) ? "a law or regulation on " + words
                    : "\"" + words + "\"";
            if (!parts.contains(part)) {
                parts.add(part);
            }
        }
        if (!parts.isEmpty()) {
            return "I couldn't confirm that " + String.join(" or ", parts) + " exists.";
        }
        return unverified.stream().anyMatch(p -> p.kind().equals(Premise.GUARANTEE)) ? GUARANTEE_CALLOUT
                : ASSUMPTION_CALLOUT;
    }

    /** The refusal for the out-of-scope categories ({@code ", ".join(oos).replace("_", " ") or "these products"}). */
    public static String refuseOutOfScope(List<String> outOfScope) {
        String oos = String.join(", ", outOfScope).replace("_", " ");
        return REFUSE_OUT_OF_SCOPE.replace("{oos}", oos.isEmpty() ? "these products" : oos);
    }

    /** An {@code ASK_PROFILE} draft that starts with {@link #WHY_ASK_PROFILE}. */
    public static String withProfileReason(String draft) {
        String text = draft.strip();
        return text.startsWith(WHY_ASK_PROFILE) ? text : (WHY_ASK_PROFILE + " " + text).strip();
    }

    /**
     * A recommendation draft that ends with the limitations sentence; unchanged without limitations or when it
     * already ends with it (a regeneration may repeat the previous answer).
     */
    public static String withLimitations(String draft, List<String> limitations) {
        if (limitations.isEmpty()) {
            return draft;
        }
        String sentence = LIMITATIONS.replace("{fields}", String.join(", ", limitations).replace('_', ' '));
        String text = draft.strip();
        return text.endsWith(sentence) ? text : text + "\n\n" + sentence;
    }

    /** The profile question for the missing fields, with the default text when none are known. */
    public static String askProfile(List<String> missingFields) {
        String missing = String.join(", ", missingFields).replace('_', ' ');
        return ASK_PROFILE.replace("{missing}", missing.isEmpty() ? DEFAULT_MISSING : missing);
    }
}
