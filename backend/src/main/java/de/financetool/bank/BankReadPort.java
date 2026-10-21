package de.financetool.bank;

import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;

/** Module API: read the persona's sandbox bank data. The only way other modules see bank data. */
public interface BankReadPort {

    /**
     * The persona's bank data filtered to {@code asOf}: accounts opened, contracts and savings plans started, and
     * transactions booked on or before {@code asOf}; account balances are opening balance plus those transactions.
     * Three limits apply, since the stored data is not versioned over time:
     * <ul>
     * <li>contracts carry their stored {@code nextDueDate} (as of the fixture's simulated date), not the next due
     * date as seen from {@code asOf};</li>
     * <li>holdings exist only from their single {@code valuationDate} on, with that one valuation;</li>
     * <li>a DEPOT account's balance is its cash only (0.00 in the fixtures); its value lies in the holdings.</li>
     * </ul>
     */
    BankSnapshot snapshot(PersonaId personaId, LocalDate asOf);
}
