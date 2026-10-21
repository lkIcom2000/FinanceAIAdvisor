package de.financetool.bank;

import de.financetool.shared.domain.PersonaId;
import java.time.LocalDate;

/** Module API: read the persona's sandbox bank data. The only way other modules see bank data. */
public interface BankReadPort {

    /**
     * The persona's bank data as of the end of {@code asOf}: only accounts, contracts, savings plans and transactions
     * dated on or before {@code asOf}, holdings valued on or before it, and account balances computed as opening
     * balance plus those transactions.
     */
    BankSnapshot snapshot(PersonaId personaId, LocalDate asOf);
}
