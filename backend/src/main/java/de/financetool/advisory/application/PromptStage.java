package de.financetool.advisory.application;

import de.financetool.advisory.adapter.ChatRepository;
import de.financetool.advisory.adapter.PromptFiles;
import de.financetool.advisory.domain.ChatMessage;
import de.financetool.advisory.domain.ClassInstruction;
import de.financetool.advisory.domain.ExposureFilter;
import de.financetool.advisory.domain.NumberLedger;
import de.financetool.advisory.domain.NumberParser;
import de.financetool.advisory.domain.PhrasingPrompt;
import de.financetool.advisory.domain.PromptPayload;
import de.financetool.advisory.domain.ToolResults;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.knowledge.RuleParamPort;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.config.AgentConfiguration;
import de.financetool.shared.config.DataExposureLevel;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Stage 5 of a turn ({@link TurnOrchestrator}): the exposure filter, the phrasing prompt and the
 * Number Check ledger.
 */
@Component
class PromptStage {

    private final ChatRepository chats;
    private final PromptFiles prompts;
    private final Profiles profiles;
    private final RuleParamPort ruleParameters;

    PromptStage(ChatRepository chats, PromptFiles prompts, Profiles profiles, RuleParamPort ruleParameters) {
        this.chats = chats;
        this.prompts = prompts;
        this.profiles = profiles;
        this.ruleParameters = ruleParameters;
    }

    /**
     * Applies the Data Exposure Level to the message, the last released turns, the profile context and the Core
     * results, and fits the phrasing prompt into the context budget (FR-43, FR-45, AD-5); the system part ends with
     * the class instruction. The Core results go in as {@code TOOL RESULTS}, the stored profile as profile context
     * (as {@code pipeline.py}); no passages exist yet. The
     * turn's {@link NumberLedger} is built from the same inputs plus the Rule Parameters valid at the as-of date, with
     * every Core number first (label: its {@code provId}).
     */
    TurnState exposureFilter(RequestContext context, PersonaScope scope, ChatMessage question, TurnState turn) {
        AgentConfiguration agent = context.agentConfig();
        List<PromptPayload.HistoryTurn> history =
                chats.releasedTurnsBefore(scope, question.id(), ExposureFilter.HISTORY_TURNS);
        PromptPayload payload = ExposureFilter.apply(level(agent.dataExposure()), turn.userText(),
                turn.nlu().extraction().depersonalizedQuestion(), history, profiles.stored(scope),
                ToolResults.format(turn.tools().results()), turn.passages());
        PromptFiles.Prompt rolePrompt = prompts.rolePrompt(agent.rolePrompt());
        PromptFiles.Prompt rules = prompts.complianceRules();
        PromptFiles.Prompt instruction = turn.tools().needsStatement() != null ? prompts.needsInstruction()
                : prompts.classInstruction(turn.messageClass());
        ChatMessage.Recommendation statement = turn.tools().statement();
        String classInstruction = ClassInstruction.fill(instruction.text(), turn.classification(),
                statement == null ? null : statement.category(),
                statement == null ? "" : String.join("; ", statement.conflicts()));
        PhrasingPrompt phrasing = PhrasingPrompt.build(rolePrompt.text(), rules.text(), classInstruction, payload,
                agent.decoding().numCtx(), agent.decoding().numPredict());
        Map<String, String> hashes = Map.of(rolePrompt.fileName(), rolePrompt.hash(), rules.fileName(), rules.hash(),
                instruction.fileName(), instruction.hash());
        Map<String, BigDecimal> parameters = new LinkedHashMap<>();
        ruleParameters.allValid(context.asOf()).forEach(p -> parameters.put(p.key(), p.value()));
        // Deviation from pipeline.py: the user's numbers are those the model saw (the depersonalised question at
        // none), and the class instruction's numbers (banded premises) are sourced too.
        NumberLedger ledger = NumberLedger.of(turn.tools().numbers(), payload.message(), payload,
                phrasing.droppedHistoryTurns(), passagesInPrompt(payload, phrasing), parameters)
                .with(NumberLedger.Source.EXPOSURE, "numbers in the class instruction",
                        NumberParser.numbersIn(classInstruction));
        return turn.withPrompt(new TurnState.Prompt(payload, phrasing, new TreeMap<>(hashes), ledger))
                .ran(TurnStage.EXPOSURE_FILTER);
    }

    /** The domain's exposure level for the configured one; exhaustive, so a new level does not compile unmapped. */
    static ExposureFilter.Level level(DataExposureLevel configured) {
        return switch (configured) {
            case AGGREGATED -> ExposureFilter.Level.AGGREGATED;
            case NONE -> ExposureFilter.Level.NONE;
        };
    }

    /**
     * The passages the phrasing prompt holds after the budget ({@code len(passages_used)}): the valid citation range;
     * none means no valid source, also when the budget dropped every retrieved passage.
     */
    static int passagesInPrompt(TurnState turn) {
        return passagesInPrompt(turn.prompt().payload(), turn.prompt().phrasing());
    }

    private static int passagesInPrompt(PromptPayload payload, PhrasingPrompt phrasing) {
        return Math.max(0, payload.passages().size() - phrasing.droppedPassages());
    }
}
