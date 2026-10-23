package de.financetool.advisory.domain;

/**
 * What each compliance level switches on (AD-3; {@code FLAGS} of {@code experiments/finlab/pipeline.py}): the only
 * interpreter of the level. The Check Chain runs on the first draft at every level (shadow results); the level only
 * decides what happens with the results.
 *
 * @param ruleEngine stage 8: {@code SHADOW} records the results without consequence, {@code ENFORCING} regenerates
 *     once on a repairable failure and lets block routing act on the results
 * @param judge stage 9: whether the Block-only Judge would run (it is not implemented: Story 3.9 is skipped)
 * @param routing stage 10: whether block routing decides the outcome; without it the outcome is the class and the
 *     draft is released
 */
public record ComponentFlags(RuleEngineMode ruleEngine, boolean judge, boolean routing) {

    /** The compliance levels as the domain knows them; the application maps the configured level exhaustively. */
    public enum Level {
        A, A_B, A_B_C
    }

    public enum RuleEngineMode {
        SHADOW, ENFORCING
    }

    /** The flags of a level: {@code A} shadow/off/off, {@code A+B} enforcing/off/on, {@code A+B+C} enforcing/on/on. */
    public static ComponentFlags of(Level level) {
        return switch (level) {
            case A -> new ComponentFlags(RuleEngineMode.SHADOW, false, false);
            case A_B -> new ComponentFlags(RuleEngineMode.ENFORCING, false, true);
            case A_B_C -> new ComponentFlags(RuleEngineMode.ENFORCING, true, true);
        };
    }

    /** Whether the Rule Engine only records (level {@code A}). */
    public boolean shadowMode() {
        return ruleEngine == RuleEngineMode.SHADOW;
    }
}
