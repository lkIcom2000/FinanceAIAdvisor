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

    static final String ASK_PROFILE = "Before I can give you a personal recommendation, I need some more information "
            + "about you: {missing}. Could you tell me these? Then I'll tell you what fits your situation and why.";

    /** The default missing-fields text of {@code _route}. */
    static final String DEFAULT_MISSING = "your goals, horizon, income and expenses, emergency fund and risk tolerance";

    /** The text of a parameter proposal card (AD-24): no figures, the card's rows carry them. */
    public static final String PARAMETER_PROPOSAL = "Here's what I understood from your question. Please check the "
            + "values below, then choose Calculate, or change them first.";

    public static final String NO_VALID_SOURCE = NoValidSource.TEXT;

    private ResponseTemplates() {
    }

    /** The refusal for the out-of-scope categories ({@code ", ".join(oos).replace("_", " ") or "these products"}). */
    public static String refuseOutOfScope(List<String> outOfScope) {
        String oos = String.join(", ", outOfScope).replace("_", " ");
        return REFUSE_OUT_OF_SCOPE.replace("{oos}", oos.isEmpty() ? "these products" : oos);
    }

    /** The profile question for the missing fields, with the default text when none are known. */
    public static String askProfile(List<String> missingFields) {
        String missing = String.join(", ", missingFields);
        return ASK_PROFILE.replace("{missing}", missing.isEmpty() ? DEFAULT_MISSING : missing);
    }
}
