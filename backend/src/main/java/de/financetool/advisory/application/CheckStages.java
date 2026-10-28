package de.financetool.advisory.application;

import de.financetool.advisory.domain.ChainResult;
import de.financetool.advisory.domain.CheckChain;
import de.financetool.advisory.domain.CitationMarkers;
import de.financetool.advisory.domain.ComponentFlags;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.RegenerationFix;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.llm.LlmMessage;
import de.financetool.llm.LlmPort;
import de.financetool.llm.LlmResult;
import de.financetool.llm.LlmRole;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.ComplianceLevel;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stages 7 to 9 of a turn ({@link TurnOrchestrator}): shadow checks, Rule Engine with its one
 * regeneration, and the Block-only Judge record.
 */
@Component
class CheckStages {

    private final LlmPort llm;

    CheckStages(LlmPort llm) {
        this.llm = llm;
    }

    /**
     * Stage 7: the Check Chain runs on the first draft at every compliance level; these are the shadow results. Until
     * something changes them, they are also the final results. With retrieval off, citation markers the model added
     * anyway are removed first ({@link CitationMarkers}); the first draft in the record keeps them.
     */
    TurnState shadowChecks(RequestContext context, TurnState turn) {
        ComponentFlags flags = ComponentFlags.of(level(context.agentConfig().compliance()));
        boolean retrieval = context.agentConfig().retrieval();
        CitationMarkers.Stripped draft = retrieval ? new CitationMarkers.Stripped(turn.draft().text(), 0)
                : CitationMarkers.strip(turn.draft().text());
        ChainResult shadow = CheckChain.run(draft.text(), facts(turn, retrieval));
        return turn.withChecks(TurnState.Checks.shadow(flags, draft.text(), draft.removed(), shadow))
                .ran(TurnStage.SHADOW_CHECKS);
    }

    /**
     * Stage 8: in Shadow Mode (level {@code A}) the Rule Engine only records. Enforcing, a repairable failure (a failed
     * Number Check, a missing or invalid citation) gets exactly one regeneration with the targeted fix: the previous
     * messages, the draft as the assistant's answer and the fix as the user's, with the same decoding; the chain then
     * runs again on the regenerated text. With retrieval off the regenerated text loses its citation markers like the
     * first draft; a blank regeneration keeps the first draft's results, so the turn is routed instead of failing.
     */
    TurnState ruleEngine(RequestContext context, TurnState turn) {
        TurnState.Checks checks = turn.checks();
        // A what-if's fixed range text (no model call) is never regenerated: block routing decides on it as it is.
        if (checks.flags().shadowMode() || turn.tools().ruleParameterError() || turn.tools().range() != null) {
            return turn.ran(TurnStage.RULE_ENGINE);
        }
        Optional<String> fix = RegenerationFix.of(checks.shadow().failed(), PromptStage.passagesInPrompt(turn),
                turn.prompt().payload().toolResults(), CheckChain.investTopic(turn.nlu().extraction().topic()),
                CheckChain.protectionTopic(turn.nlu().extraction().topic()), turn.language());
        if (fix.isEmpty()) {
            return turn.ran(TurnStage.RULE_ENGINE);
        }
        PhrasingPrompt phrasing = turn.prompt().phrasing();
        LlmResult result = llm.generate(LlmRole.PHRASING,
                List.of(LlmMessage.system(phrasing.system()), LlmMessage.user(phrasing.user()),
                        LlmMessage.assistant(turn.draft().text()), LlmMessage.user(fix.get())),
                context.agentConfig().decoding(), null);
        boolean retrieval = context.agentConfig().retrieval();
        String raw = result.text() == null ? "" : result.text();
        String text = DraftStage.withFixedSentences(turn, retrieval ? raw : CitationMarkers.strip(raw).text());
        TurnState.Regeneration regeneration = new TurnState.Regeneration(fix.get(), text, result.truncated());
        ChainResult results = text.isBlank() ? checks.shadow() : CheckChain.run(text, facts(turn, retrieval));
        return turn.withChecks(checks.withRegeneration(regeneration, results)).ran(TurnStage.RULE_ENGINE);
    }

    /**
     * Stage 9: the Block-only Judge is not implemented (Story 3.9 is skipped). Under {@code A+B+C} the record says
     * so ({@code judgeNotImplemented}); there is never a verdict.
     */
    TurnState judge(TurnState turn) {
        return turn.withChecks(turn.checks().withJudgeNotImplemented(turn.checks().flags().judge()))
                .passedThrough(TurnStage.JUDGE);
    }

    /**
     * What the Check Chain needs besides the text, with the turn's ledger for the Number Check and the Core's category
     * selection of a recommendation. {@code SOURCE_VALIDITY} applies only with retrieval on, as in the experiment.
     */
    private static CheckChain.Facts facts(TurnState turn, boolean retrievalOn) {
        return CheckChain.Facts.of(turn.classification(), turn.userText(), turn.tools().needs() != null
                ? NeedsStatements.facts(turn.tools().needs())
                : Recommendations.facts(turn.tools().statement(), turn.nlu().extraction().topic()), retrievalOn,
                PromptStage.passagesInPrompt(turn), turn.prompt().ledger());
    }

    /** The domain's compliance level for the configured one; exhaustive, so a new level does not compile unmapped. */
    static ComponentFlags.Level level(ComplianceLevel configured) {
        return switch (configured) {
            case A -> ComponentFlags.Level.A;
            case A_B -> ComponentFlags.Level.A_B;
            case A_B_C -> ComponentFlags.Level.A_B_C;
        };
    }
}
