package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.core.domain.CoreResult;
import de.financetool.core.domain.WhatIfProjection;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.RequestContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stage 6 of a turn ({@link TurnOrchestrator}): the first draft and the class's fixed sentences.
 */
@Component
class DraftStage {

    private final LlmPort llm;

    DraftStage(LlmPort llm) {
        this.llm = llm;
    }

    /**
     * The phrasing role drafts the answer: prompt files as system message, everything else in the user part. A blank
     * draft fails the turn only if block routing would release it. A parameter proposal is not phrased: its draft is
     * the fixed proposal text. An {@code ASK_PROFILE} draft starts with why Fine asks; a recommendation with strict
     * profile fields still unknown ends with them (AD-23). Both sentences are fixed and run through the checks; a blank
     * draft stays blank.
     */
    TurnState llmDraft(RequestContext context, TurnState turn) {
        if (turn.tools().proposal()) {
            return turn.withDraft(new TurnState.Draft(ResponseTemplates.PARAMETER_PROPOSAL, null, null, false))
                    .passedThrough(TurnStage.LLM_DRAFT);
        }
        if (turn.tools().range() != null) {
            return turn.withDraft(new TurnState.Draft(rangeText(turn.tools().results()), null, null, false))
                    .passedThrough(TurnStage.LLM_DRAFT);
        }
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user())),
                context.agentConfig().decoding(), null);
        String text = withFixedSentences(turn, result.text() == null ? "" : result.text());
        return turn.withDraft(new TurnState.Draft(text, result.modelTag(), result.digest(), result.truncated()))
                .ran(TurnStage.LLM_DRAFT);
    }

    /** The fixed answer of a calculated what-if, from the what-if projection's outputs (the last result). */
    static String rangeText(List<CoreResult> results) {
        CoreResult whatIf = results.getLast();
        return ResponseTemplates.range(whatIf.number(WhatIfProjection.OUTPUT_P10).value(),
                whatIf.number(WhatIfProjection.OUTPUT_P90).value(), whatIf.number(WhatIfProjection.OUTPUT_P50).value(),
                whatIf.number(WhatIfProjection.OUTPUT_YEARS).value().intValueExact());
    }

    /** The class's fixed sentences around a model text (first draft and regeneration alike); blank stays blank. */
    static String withFixedSentences(TurnState turn, String text) {
        if (text.isBlank()) {
            return text;
        }
        return switch (turn.messageClass()) {
            case ASK_PROFILE -> ResponseTemplates.withProfileReason(text);
            case RECOMMEND_CATEGORY -> {
                ChatMessage.Recommendation statement = turn.tools().statement();
                String led = turn.tools().needsStatement() != null
                        ? ResponseTemplates.withRecommendationLead(text, ResponseTemplates.NEEDS_LEAD)
                        : statement == null ? text : ResponseTemplates.withRecommendationLead(text,
                        ResponseTemplates.recommendationLead(statement.label(), statement.conflicts()));
                yield ResponseTemplates.withLimitations(led, turn.classification().limitations());
            }
            default -> text;
        };
    }
}
