package de.financetool.advisory.application;

import de.financetool.advisory.domain.CheckChain;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.ToolPlan;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.knowledge.NoValidRuleParameterException;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stage 3 of a turn ({@link TurnOrchestrator}): the Core tools and figures the turn runs on.
 */
@Component
class ToolPlanStage {

    private final ToolRegistry tools;
    private final Profiles profiles;
    private final Recommendations recommendations;
    private final NeedsStatements needsStatements;

    ToolPlanStage(ToolRegistry tools, Profiles profiles, Recommendations recommendations,
            NeedsStatements needsStatements) {
        this.tools = tools;
        this.profiles = profiles;
        this.recommendations = recommendations;
        this.needsStatements = needsStatements;
    }

    /**
     * Stage 3, only for {@code COMPUTE}: parameters from the message become a proposal and nothing is computed
     * (AD-24); a confirmed spec runs its whitelisted tool through the {@link ToolRegistry}. A tool missing a valid
     * Rule Parameter version marks the turn for {@code ESCALATE}. Before that, for every class but refusals and
     * traps, a subsidy question about a later year needs the Core's subsidy rules for that year (UJ-5); without them
     * the turn escalates too. A {@code RECOMMEND_CATEGORY} turn runs the Core's category selection and cost
     * calculation on the effective profile ({@link Recommendations}); on a protection topic the Core's
     * demands-and-needs statement instead ({@link NeedsStatements}). An {@code INFORM} turn about the user on a topic
     * with a rule ({@link ToolPlan#personalFigures}) runs on the persona's Core figures for that topic (FR-62); they
     * reach the prompt as {@code TOOL RESULTS}, the Number Check and the Audit Record, but no figure chips. Missing a
     * valid Rule Parameter there leaves the turn without figures.
     */
    TurnState toolPlan(RequestContext context, PersonaScope scope, TurnState turn) {
        TurnState.Tools planned = turn.tools();
        MessageClass messageClass = turn.messageClass();
        Optional<LocalDate> laterYear = messageClass == MessageClass.REFUSE_OUT_OF_SCOPE
                || messageClass == MessageClass.DETECT_TRAP ? Optional.empty()
                : ToolPlan.laterSubsidyYear(turn.nlu().extraction(), turn.userText(), context.asOf());
        if (laterYear.isPresent()) {
            try {
                tools.requireSubsidyRules(laterYear.get());
            } catch (NoValidRuleParameterException e) {
                return turn.withTools(TurnState.Tools.laterYearRuleMissing()).ran(TurnStage.TOOL_PLAN);
            }
        }
        if (messageClass == MessageClass.RECOMMEND_CATEGORY
                && CheckChain.protectionTopic(turn.nlu().extraction().topic())) {
            return turn.withTools(needsStatements.run(context, profiles.stored(scope),
                    turn.nlu().extraction().statedProfile())).ran(TurnStage.TOOL_PLAN);
        }
        if (messageClass == MessageClass.RECOMMEND_CATEGORY) {
            return turn.withTools(recommendations.run(context, profiles.stored(scope),
                    turn.nlu().extraction().statedProfile(), turn.nlu().extraction().topic()))
                    .ran(TurnStage.TOOL_PLAN);
        }
        Optional<ToolPlan.PersonalFigures> personal = ToolPlan.personalFigures(messageClass, turn.nlu().extraction());
        if (personal.isPresent()) {
            try {
                return turn.withTools(new TurnState.Tools(null, null, null, false,
                        tools.personalFigures(context, personal.get()), false)).ran(TurnStage.TOOL_PLAN);
            } catch (NoValidRuleParameterException e) {
                // Without the rule's figures the explanation stays general, as without a rule; no escalation.
                return turn.passedThrough(TurnStage.TOOL_PLAN);
            }
        }
        if (turn.messageClass() != MessageClass.COMPUTE) {
            return turn.passedThrough(TurnStage.TOOL_PLAN);
        }
        if (planned.spec() == null) {
            // A what-if with a subsidy row needs this year's subsidy rules; without them the proposal would only
            // escalate at Calculate, so the turn escalates now.
            if (planned.planned().subsidyAsked()) {
                try {
                    tools.requireSubsidyRules(context.asOf());
                } catch (NoValidRuleParameterException e) {
                    return turn.withTools(new TurnState.Tools(null, null, null, false, List.of(), true))
                            .ran(TurnStage.TOOL_PLAN);
                }
            }
            // A what-if's subsidy eligibility is account data, read for the card like the 3.7 portfolio.
            SourcedNumber netIncome = planned.planned().subsidyAsked() ? tools.netIncome(context) : null;
            SimulationSpec spec = ToolPlan.spec(planned.planned(), planned.accountPortfolio(), netIncome);
            return turn.withTools(new TurnState.Tools(planned.planned(), planned.accountPortfolio(), spec, true,
                    List.of(), false)).ran(TurnStage.TOOL_PLAN);
        }
        try {
            ToolRegistry.Ran ran = tools.run(context, planned.spec());
            return turn.withTools(TurnState.Tools.calculated(planned.spec(), ran.results(), ran.range()))
                    .ran(TurnStage.TOOL_PLAN);
        } catch (NoValidRuleParameterException e) {
            return turn.withTools(new TurnState.Tools(null, null, planned.spec(), false, List.of(), true))
                    .ran(TurnStage.TOOL_PLAN);
        }
    }
}
