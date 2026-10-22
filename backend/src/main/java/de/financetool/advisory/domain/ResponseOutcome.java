package de.financetool.advisory.domain;

/** How a released turn ends: exactly one per turn, one of the six message classes or {@link #ESCALATE}. */
public enum ResponseOutcome {
    INFORM, ASK_PROFILE, RECOMMEND_CATEGORY, COMPUTE, REFUSE_OUT_OF_SCOPE, DETECT_TRAP, ESCALATE;

    /** The outcome when block routing does not change the class (pass-through routing). */
    public static ResponseOutcome of(MessageClass messageClass) {
        return valueOf(messageClass.name());
    }
}
