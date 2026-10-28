package de.financetool.advisory.application;

import de.financetool.advisory.domain.BlockRouting;
import de.financetool.advisory.domain.NoValidSource;
import de.financetool.advisory.domain.ResponseOutcome;
import de.financetool.advisory.domain.ResponseTemplates;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stage 10 of a turn ({@link TurnOrchestrator}): what is released, the draft or a template.
 */
@Component
class BlockRoutingStage {

    /**
     * Stage 10: with routing on, {@link BlockRouting} decides on the final results: a routed turn releases its
     * template (the no-valid-source one with a related in-scope chip) and keeps no final results; otherwise the
     * (regenerated) draft is released with the class as outcome. With routing off (level {@code A}) the outcome is
     * the class and the draft is released, except that a Rule Parameter error escalates at every level: its draft has
     * no figures. A blank text that would be released fails the turn.
     */
    TurnState blockRouting(TurnState turn) {
        TurnState.Checks checks = turn.checks();
        if (!checks.flags().routing() && !turn.tools().ruleParameterError()) {
            ResponseOutcome outcome = ResponseOutcome.of(turn.messageClass());
            return turn.withOutcome(outcome, releasable(checks.draftText()), chips(outcome, turn), false)
                    .passedThrough(TurnStage.BLOCK_ROUTING);
        }
        BlockRouting.Route route = BlockRouting.route(checks.finalResults().failed(), turn.classification(),
                turn.tools().ruleParameterError(), turn.tools().ruleForLaterYear(), turn.language());
        TurnState routed = turn.withChecks(checks.withRoute(route));
        if (route.released()) {
            String text = checks.routedDraft();
            ResponseOutcome outcome = ResponseOutcome.of(turn.messageClass());
            return routed.withOutcome(outcome, releasable(text), chips(outcome, turn), false)
                    .ran(TurnStage.BLOCK_ROUTING);
        }
        List<String> chips = route.noValidSource()
                ? List.of(NoValidSource.suggestion(turn.nlu().extraction().topic(), turn.userText(), turn.language()))
                : chips(route.outcome(), turn);
        return routed.withOutcome(route.outcome(), route.text(), chips, route.noValidSource())
                .ran(TurnStage.BLOCK_ROUTING);
    }

    /**
     * The fixed suggestion chips of an outcome (UX-DR11): a refusal offers the in-scope next steps, a trap the related
     * in-scope question of its topic; other outcomes have none.
     */
    private static List<String> chips(ResponseOutcome outcome, TurnState turn) {
        return switch (outcome) {
            case REFUSE_OUT_OF_SCOPE -> ResponseTemplates.refusalSuggestions(turn.language());
            case DETECT_TRAP -> List.of(NoValidSource.suggestion(turn.nlu().extraction().topic(), turn.userText(),
                    turn.language()));
            default -> List.of();
        };
    }

    private static String releasable(String text) {
        if (text.isBlank()) {
            throw new IllegalStateException("The phrasing model returned an empty answer");
        }
        return text.strip();
    }
}
