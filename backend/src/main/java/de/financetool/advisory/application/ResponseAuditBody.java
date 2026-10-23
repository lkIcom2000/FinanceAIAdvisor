package de.financetool.advisory.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.shared.config.DataExposureLevel;
import java.util.List;
import java.util.SortedMap;

/**
 * The body of a turn's {@code RESPONSE} Audit Record (AD-13). Judge verdict and parse status are absent while the
 * Judge does not run.
 *
 * @param evidence the {@code provId}s of the turn's evidence items
 * @param checks the Check Chain results (empty until the Rule Engine runs)
 * @param promptHashes file name of each prompt file in the phrasing call to the sha256 of its LF bytes
 * @param truncated whether the model or the prompt budget truncated anything
 * @param droppedHistoryTurns history turns the prompt budget dropped
 * @param droppedPassages Knowledge Base passages the prompt budget dropped
 * @param dataExposure the Data Exposure Level applied to the phrasing prompt
 * @param messageWithheld whether level {@code NONE} withheld the user's message entirely
 * @param complianceLevel the configured level, e.g. {@code A+B+C}
 * @param passThroughStages the stages that ran as explicit pass-throughs, in pipeline order
 */
record ResponseAuditBody(
        @JsonProperty("class") MessageClass messageClass,
        ResponseOutcome outcome,
        List<String> evidence,
        List<Object> checks,
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
        String complianceLevel,
        List<TurnStage> passThroughStages) {
}
