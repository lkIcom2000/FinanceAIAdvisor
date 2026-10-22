package de.financetool.advisory.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.TurnStage;
import java.util.List;

/**
 * The body of a turn's {@code RESPONSE} Audit Record (AD-13). Judge verdict and parse status are absent while the
 * Judge does not run.
 *
 * @param evidence the {@code provId}s of the turn's evidence items
 * @param checks the Check Chain results (empty until the Rule Engine runs)
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
        String promptHash,
        boolean truncated,
        String firstDraft,
        String finalText,
        boolean regenerated,
        String complianceLevel,
        List<TurnStage> passThroughStages) {
}
