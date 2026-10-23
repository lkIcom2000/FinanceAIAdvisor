package de.financetool.advisory.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.financetool.advisory.domain.CheckId;
import de.financetool.advisory.domain.CheckResult;
import de.financetool.advisory.domain.ComponentFlags;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.shared.config.DataExposureLevel;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * The body of a turn's {@code RESPONSE} Audit Record (AD-13). Judge verdict and parse status are absent while the
 * Judge does not run (Story 3.9 is skipped).
 *
 * @param classReasons the facts that led to the class
 * @param missingFields profile fields an {@code ASK_PROFILE} turn asks for
 * @param unverifiedPremises the premises that made the turn {@code DETECT_TRAP}
 * @param outOfScope the out-of-scope product categories of a {@code REFUSE_OUT_OF_SCOPE} turn
 * @param nlu the grounded NLU extraction (the experiment's keys)
 * @param nluGrounding what the grounding rules changed in the NLU's proposal
 * @param nluModelTag the NLU model's tag
 * @param nluModelDigest the NLU model's pinned digest
 * @param nluPromptHashes file name of each NLU prompt file to the sha256 of its LF bytes
 * @param nluCacheHit whether the extraction came from the NLU cache without a model call
 * @param nluParseOk whether the NLU model's answer was a JSON object
 * @param instructionOverrideAttempt whether the message tried to override the instructions (treated as data)
 * @param noValidSource whether the no-valid-source template was released instead of the draft
 * @param evidence the {@code provId}s of the turn's evidence items
 * @param shadowChecks the Check Chain on the first draft, at every compliance level
 * @param finalChecks the Check Chain on the released text; empty when block routing released a template (it is
 *     pre-approved text, not model text)
 * @param routedOnChecks the results block routing released a template on (after a regeneration, the regenerated
 *     text's); empty when the draft was released
 * @param route block routing's decision, e.g. {@code released}, {@code block_scope}, {@code no_valid_source}
 * @param firedChecks the checks whose failure decided a routed turn
 * @param shadowMode whether the Rule Engine ran in Shadow Mode (level {@code A}): recorded, never enforced
 * @param componentFlags what the compliance level switched on
 * @param judgeNotImplemented whether the level asked for the Judge, which is not implemented (Story 3.9)
 * @param citationMarkersRemoved citation markers removed from the first draft with retrieval off (the first draft
 *     keeps them)
 * @param promptHashes file name of each prompt file in the phrasing call to the sha256 of its LF bytes
 * @param truncated whether the model or the prompt budget truncated anything
 * @param droppedHistoryTurns history turns the prompt budget dropped
 * @param droppedPassages Knowledge Base passages the prompt budget dropped
 * @param dataExposure the Data Exposure Level applied to the phrasing prompt
 * @param messageWithheld whether level {@code NONE} withheld the user's message entirely
 * @param regenerated whether the Rule Engine regenerated the draft once
 * @param regenerationFix the targeted instruction the regeneration was asked with
 * @param regeneratedText the regenerated text, before block routing
 * @param complianceLevel the configured level, e.g. {@code A+B+C}
 * @param passThroughStages the stages that ran as explicit pass-throughs, in pipeline order
 */
record ResponseAuditBody(
        @JsonProperty("class") MessageClass messageClass,
        ResponseOutcome outcome,
        List<String> classReasons,
        List<String> missingFields,
        List<Map<String, Object>> unverifiedPremises,
        List<String> outOfScope,
        Map<String, Object> nlu,
        List<String> nluGrounding,
        String nluModelTag,
        String nluModelDigest,
        SortedMap<String, String> nluPromptHashes,
        boolean nluCacheHit,
        boolean nluParseOk,
        boolean instructionOverrideAttempt,
        boolean noValidSource,
        List<String> evidence,
        List<CheckResult> shadowChecks,
        List<CheckResult> finalChecks,
        List<CheckResult> routedOnChecks,
        String route,
        List<CheckId> firedChecks,
        boolean shadowMode,
        ComponentFlags componentFlags,
        boolean judgeNotImplemented,
        int citationMarkersRemoved,
        String modelTag,
        String modelDigest,
        SortedMap<String, String> promptHashes,
        boolean truncated,
        int droppedHistoryTurns,
        int droppedPassages,
        DataExposureLevel dataExposure,
        boolean messageWithheld,
        String firstDraft,
        String finalText,
        boolean regenerated,
        String regenerationFix,
        String regeneratedText,
        String complianceLevel,
        List<TurnStage> passThroughStages) {
}
