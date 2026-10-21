package de.financetool.bank;

import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The persona's sandbox bank data filtered to {@link #asOf} (see {@link BankReadPort#snapshot}). Money has scale 2.
 * Contracts carry their stored {@code nextDueDate}, holdings appear only from their single {@code valuationDate}, and
 * a DEPOT account's balance is cash only (its value lies in the holdings).
 */
public record BankSnapshot(PersonaId personaId, LocalDate asOf, List<Account> accounts, List<Contract> contracts,
        List<Transaction> transactions, List<Holding> holdings, List<SavingsPlan> savingsPlans) {

    public BankSnapshot {
        accounts = List.copyOf(accounts);
        contracts = List.copyOf(contracts);
        transactions = List.copyOf(transactions);
        holdings = List.copyOf(holdings);
        savingsPlans = List.copyOf(savingsPlans);
    }

    /** {@code balance} = opening balance + transactions up to {@code asOf}; cash only (0.00 for a DEPOT). */
    public record Account(UUID id, AccountType type, String name, String iban, LocalDate openingDate,
            BigDecimal openingBalance, BigDecimal balance) {
    }

    /**
     * {@code premium}, {@code paymentInterval}, {@code nextDueDate} and {@code debitAccountId} may be null (e.g.
     * co-insured on someone else's policy); {@code nextDueDate} is the stored one, not recomputed for {@code asOf}.
     */
    public record Contract(UUID id, Coverage coverage, String provider, String policyNumber, String policyholder,
            InsuredRole insuredRole, BigDecimal premium, PaymentInterval paymentInterval, LocalDate nextDueDate,
            UUID debitAccountId, LocalDate startDate) {
    }

    /** {@code recurrenceId} marks a monthly standing order or direct debit, {@code contractId} a premium debit. */
    public record Transaction(UUID id, UUID accountId, LocalDate bookingDate, BigDecimal amount,
            TransactionCategory category, String counterparty, String reference, String recurrenceId,
            UUID contractId, TransactionOrigin origin) {
    }

    public record Holding(UUID id, UUID accountId, String name, AssetClass assetClass, BigDecimal units,
            BigDecimal unitPrice, LocalDate valuationDate, BigDecimal costBasis) {
    }

    public record SavingsPlan(UUID id, UUID accountId, UUID debitAccountId, String name, AssetClass assetClass,
            BigDecimal amount, int dayOfMonth, LocalDate startDate, String recurrenceId) {
    }
}
