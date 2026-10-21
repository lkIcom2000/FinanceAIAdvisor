package de.financetool.bank;

import de.financetool.shared.domain.PersonaId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The persona's sandbox bank data as of {@link #asOf} (see {@link BankReadPort#snapshot}). Money has scale 2. */
public record BankSnapshot(PersonaId personaId, LocalDate asOf, List<Account> accounts, List<Contract> contracts,
        List<Transaction> transactions, List<Holding> holdings, List<SavingsPlan> savingsPlans) {

    public BankSnapshot {
        accounts = List.copyOf(accounts);
        contracts = List.copyOf(contracts);
        transactions = List.copyOf(transactions);
        holdings = List.copyOf(holdings);
        savingsPlans = List.copyOf(savingsPlans);
    }

    /** {@code balance} = opening balance + transactions up to {@code asOf}. */
    public record Account(UUID id, AccountType type, String name, String iban, LocalDate openingDate,
            BigDecimal openingBalance, BigDecimal balance) {
    }

    /** {@code premium}, {@code paymentInterval}, {@code nextDueDate} and {@code debitAccountId} may be null. */
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
