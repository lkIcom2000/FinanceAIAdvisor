package de.financetool.advisory.application;

import de.financetool.advisory.domain.Classification;
import de.financetool.advisory.domain.Classifier;
import de.financetool.advisory.domain.EntityRegistry;
import de.financetool.advisory.domain.MessageClass;
import de.financetool.advisory.domain.ProfileCompleteness;
import de.financetool.advisory.domain.ToolPlan;
import de.financetool.advisory.domain.TurnStage;
import de.financetool.advisory.domain.TurnState;
import de.financetool.profile.Profiles;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SourcedNumber;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stage 2 of a turn ({@link TurnOrchestrator}): the message class and the computable plan.
 */
@Component
class ClassifyStage {

    private final ToolRegistry tools;
    private final Profiles profiles;
    private final ProfileCompleteness completeness;
    private final EntityRegistry registry;

    ClassifyStage(ToolRegistry tools, Profiles profiles, ProfileCompleteness completeness, EntityRegistry registry) {
        this.tools = tools;
        this.profiles = profiles;
        this.completeness = completeness;
        this.registry = registry;
    }

    /**
     * Pure rules pick exactly one class (FR-9) on the persona's stored profile and the facts the message states
     * (AD-23; the stated facts are never stored); a persona workspace turn (no acceptance run) follows the strict
     * FR-17 rule. A message is computable if the pure
     * {@link ToolPlan} has every parameter (the experiment plans before it classifies); a portfolio from account
     * data needs investments above zero, so their value is read here. A confirmed proposal is {@code COMPUTE}.
     */
    TurnState classify(RequestContext context, PersonaScope scope, TurnState turn,
            TurnOrchestrator.Confirmation confirmation) {
        if (confirmation != null) {
            Classification confirmed = new Classification(MessageClass.COMPUTE,
                    List.of("parameter proposal confirmed"), List.of(), List.of(), List.of(),
                    turn.nlu().extraction().topic());
            return turn.withClassification(confirmed)
                    .withTools(new TurnState.Tools(null, null, confirmation.spec(), false, List.of(), false))
                    .ran(TurnStage.CLASSIFY);
        }
        Optional<ToolPlan.Planned> planned = ToolPlan.plan(turn.nlu().extraction(), turn.userText());
        SourcedNumber accountPortfolio = null;
        if (planned.isPresent() && planned.get().portfolioFromAccount()) {
            accountPortfolio = tools.accountPortfolio(context).filter(f -> f.value().signum() > 0).orElse(null);
            if (accountPortfolio == null) {
                planned = Optional.empty();
            }
        }
        Classification classification = Classifier.classify(turn.nlu().extraction(), profiles.stored(scope),
                completeness, registry, planned.isPresent(), turn.userText(), context.runId() == null);
        return turn.withClassification(classification)
                .withTools(new TurnState.Tools(planned.orElse(null), accountPortfolio, null, false, List.of(), false))
                .ran(TurnStage.CLASSIFY);
    }
}
