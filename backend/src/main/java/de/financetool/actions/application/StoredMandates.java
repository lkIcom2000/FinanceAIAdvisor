package de.financetool.actions.application;

import de.financetool.actions.adapter.MandateRepository;
import de.financetool.actions.domain.GateAction;
import de.financetool.actions.domain.MandateBounds;
import de.financetool.actions.domain.RuleTemplate;
import de.financetool.shared.PersonaScope;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The Mandate lookup over the stored rules: the active rule of the Action's template covers it on its accounts. */
@Component
class StoredMandates implements MandateLookup {

    private final MandateRepository mandates;

    StoredMandates(MandateRepository mandates) {
        this.mandates = mandates;
    }

    @Override
    public Optional<MandateBounds> covering(PersonaScope scope, GateAction action, UUID sourceAccountId,
            UUID targetAccountId, LocalDate asOf) {
        return Arrays.stream(RuleTemplate.values())
                .filter(t -> t.actionType() == action.type())
                .findFirst()
                .flatMap(t -> mandates.activeOf(scope, t))
                .filter(row -> Objects.equals(row.sourceAccountId(), sourceAccountId)
                        && Objects.equals(row.targetAccountId(), targetAccountId))
                .map(row -> new MandateBounds(row.id(), row.template(), row.bounds().maxPerAction(),
                        row.bounds().maxPerMonth(), row.bounds().floor(),
                        mandates.executedSum(scope, row.id(), YearMonth.from(asOf))));
    }

    @Override
    public int active(PersonaScope scope) {
        return mandates.countActive(scope);
    }
}
