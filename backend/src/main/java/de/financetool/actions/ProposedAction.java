package de.financetool.actions;

import de.financetool.actions.domain.ActionType;
import de.financetool.actions.domain.RegulatoryClass;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Module API: an Action monitoring asks {@link Actions#propose} to create.
 *
 * @param amount in EUR, or {@code null} for an Action without an amount (counts as 0 in the gate)
 * @param sourceAccountId the account money leaves, or {@code null}
 * @param targetAccountId the account money reaches, or {@code null}
 * @param triggerId the Trigger that led to the Action, or {@code null}
 */
public record ProposedAction(ActionType type, RegulatoryClass regulatoryClass, BigDecimal amount,
        UUID sourceAccountId, UUID targetAccountId, UUID triggerId) {

    public ProposedAction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(regulatoryClass, "regulatoryClass");
        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
    }
}
