package de.financetool.advisory.application;

import de.financetool.advisory.domain.ToolPlan;
import de.financetool.audit.EvidenceOrigin;
import de.financetool.core.CoreGateway;
import de.financetool.core.domain.CoreResult;
import de.financetool.shared.RequestContext;
import de.financetool.shared.domain.SimulationSpec;
import de.financetool.shared.domain.SourcedNumber;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The one tool whitelist (AD-3): every Core, Knowledge Base and bank read a turn may make, mapped from the tool
 * plan's ids. The orchestrator alone calls it with a confirmed spec; the LLM only ever sees the results as text. The
 * Knowledge Base joins with retrieval (Epic 4).
 */
@Component
public class ToolRegistry {

    /** The outputs of the withdrawal duration that only repeat its inputs; they are no figure chips. */
    static final Set<String> ECHOED_INPUTS = Set.of("portfolio", "monthlyWithdrawal", "annualRate");

    private final CoreGateway core;

    ToolRegistry(CoreGateway core) {
        this.core = core;
    }

    /** Account data read: the value of the persona's investments today, if they have any. */
    public Optional<SourcedNumber> accountPortfolio(RequestContext context) {
        return core.investmentTotals(context, EvidenceOrigin.TURN)
                .map(totals -> totals.number(CoreGateway.INVESTMENTS_TODAY));
    }

    /**
     * Checks that the Core has subsidy rules valid on {@code date}, without computing.
     *
     * @throws de.financetool.knowledge.NoValidRuleParameterException if it has none
     */
    public void requireSubsidyRules(LocalDate date) {
        core.requireSubsidyRules(date);
    }

    /**
     * Runs the spec's tool. An account-data portfolio is read again, so its figure is part of the turn; if the
     * figure changed since the proposal, the confirmed value runs as a stated one.
     *
     * @return the Core results in run order
     * @throws IllegalArgumentException for a function outside the whitelist
     * @throws de.financetool.knowledge.NoValidRuleParameterException if a tool needs a Rule Parameter without a
     *     version valid at the as-of date
     */
    public List<CoreResult> run(RequestContext context, SimulationSpec spec) {
        ToolPlan.Tool tool = ToolPlan.Tool.ofFunction(spec.function())
                .orElseThrow(() -> new IllegalArgumentException("Not a whitelisted tool: " + spec.function()));
        List<CoreResult> results = new ArrayList<>();
        switch (tool) {
            case WITHDRAWAL_DURATION -> {
                SimulationSpec.Parameter portfolio = spec.parameter(ToolPlan.PORTFOLIO);
                SourcedNumber figure = null;
                if (portfolio.source() == SimulationSpec.Source.ACCOUNT_DATA) {
                    Optional<CoreResult> totals = core.investmentTotals(context, EvidenceOrigin.TURN);
                    if (totals.isPresent()
                            && totals.get().number(CoreGateway.INVESTMENTS_TODAY).provId().equals(portfolio.provId())) {
                        results.add(totals.get());
                        figure = totals.get().number(CoreGateway.INVESTMENTS_TODAY);
                    }
                }
                results.add(core.withdrawalDuration(context, EvidenceOrigin.TURN, portfolio.value(), figure,
                        spec.parameter(ToolPlan.MONTHLY_WITHDRAWAL).value(),
                        spec.parameter(ToolPlan.ANNUAL_RATE).value()));
            }
        }
        return results;
    }

    /**
     * The figures a calculated answer lists (its figure chips): the account-data figure the spec used and every
     * computed output, without the outputs that repeat an input.
     */
    public static List<SourcedNumber> figures(SimulationSpec spec, List<CoreResult> results) {
        Set<String> accountIds = new HashSet<>();
        spec.parameters().stream().filter(p -> p.provId() != null).forEach(p -> accountIds.add(p.provId()));
        List<SourcedNumber> figures = new ArrayList<>();
        for (CoreResult result : results) {
            for (int i = 0; i < result.numbers().size(); i++) {
                SourcedNumber number = result.numbers().get(i);
                boolean computed = result.function().equals(ToolPlan.Tool.WITHDRAWAL_DURATION.function())
                        && !ECHOED_INPUTS.contains(result.outputs().get(i).outputKey());
                if (computed || accountIds.contains(number.provId())) {
                    figures.add(number);
                }
            }
        }
        return figures;
    }
}
