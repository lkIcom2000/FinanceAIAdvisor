package de.financetool.core.domain;

import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The keyed results the assessment card pre-fills from account data (Story 6.2), from one {@code CoreGateway} call.
 *
 * @param netIncome output {@link MonthlyNetIncome#OUTPUT}
 * @param emergencyFund outputs {@link EmergencyFundTarget#OUTPUT_CURRENT} and
 *        {@link EmergencyFundTarget#OUTPUT_MONTHLY_EXPENSES}
 */
@NamedInterface("results")
public record ProfileFigures(CoreResult netIncome, CoreResult emergencyFund) {

    public ProfileFigures {
        Objects.requireNonNull(netIncome, "netIncome");
        Objects.requireNonNull(emergencyFund, "emergencyFund");
    }
}
