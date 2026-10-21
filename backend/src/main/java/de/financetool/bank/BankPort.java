package de.financetool.bank;

import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Module API: the only way to change sandbox bank data. Runs in the calling use case's transaction. */
public interface BankPort {

    /**
     * Books a transfer between two accounts of the persona as two {@link TransactionCategory#TRANSFER} legs with
     * origin {@link TransactionOrigin#BANK_PORT}.
     *
     * @throws IllegalArgumentException if an account does not belong to the persona, both accounts are the same or
     *         {@code amount} is not positive; nothing is booked then
     */
    Transfer transfer(PersonaId personaId, UUID fromAccountId, UUID toAccountId, BigDecimal amount,
            LocalDate bookingDate, String reference);

    /** The two booked legs. */
    record Transfer(UUID debitTransactionId, UUID creditTransactionId) {
    }
}
