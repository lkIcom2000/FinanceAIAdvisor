package de.financetool.advisory.domain;

/**
 * The turn pipeline (AR6) in its fixed order: the dialog state is consulted first, then the 11 stages. The
 * orchestrator runs them in this declaration order; a stage that is not implemented yet is an explicit pass-through
 * and is named in the Audit Record.
 */
public enum TurnStage {
    DIALOG_STATE,
    NLU,
    CLASSIFY,
    TOOL_PLAN,
    KB_RETRIEVE,
    EXPOSURE_FILTER,
    LLM_DRAFT,
    SHADOW_CHECKS,
    RULE_ENGINE,
    JUDGE,
    BLOCK_ROUTING,
    AUDIT_RELEASE
}
