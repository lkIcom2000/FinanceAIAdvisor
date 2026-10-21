package de.financetool.shared.fixtures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One Synthetic Persona fixture ({@code data/personas/<personaId>.json}), mirroring {@code data/personas/schema.json}.
 * The single source of the persona's bank rows, start date ({@link #simulatedDate}) and Planted Ground Truth (FR-51).
 * Enumerated values stay strings here; the schema closes them and the owning module maps them to its own enums.
 */
public record PersonaFixture(int schemaVersion, String personaId, String description, Generator generator,
        LocalDate simulatedDate, List<Account> accounts, List<Contract> contracts, List<Transaction> transactions,
        List<Holding> holdings, List<SavingsPlan> savingsPlans, List<PlantedGroundTruth> plantedGroundTruth) {

    public PersonaFixture {
        accounts = List.copyOf(accounts);
        contracts = List.copyOf(contracts);
        transactions = List.copyOf(transactions);
        holdings = List.copyOf(holdings);
        savingsPlans = List.copyOf(savingsPlans);
        plantedGroundTruth = List.copyOf(plantedGroundTruth);
    }

    public record Generator(long seed, int generatorVersion) {
    }

    /** {@code type}: CURRENT, OVERNIGHT or DEPOT. */
    public record Account(UUID id, String type, String name, String iban, LocalDate openingDate,
            BigDecimal openingBalance) {
    }

    /**
     * An insurance or pension contract. {@code premium}, {@code paymentInterval}, {@code nextDueDate} and
     * {@code debitAccountId} are absent together, e.g. where the persona is co-insured on someone else's policy.
     */
    public record Contract(UUID id, String coverage, String provider, String policyNumber, String policyholder,
            String insuredRole, BigDecimal premium, String paymentInterval, LocalDate nextDueDate,
            UUID debitAccountId, LocalDate startDate) {
    }

    /**
     * A booking. {@code recurrenceId} marks a monthly standing order or direct debit, {@code contractId} a premium
     * debit; neither marks variable spending.
     */
    public record Transaction(UUID id, UUID accountId, LocalDate bookingDate, BigDecimal amount, String category,
            String counterparty, String reference, String recurrenceId, UUID contractId) {
    }

    public record Holding(UUID id, UUID accountId, String name, String assetClass, BigDecimal units,
            BigDecimal unitPrice, LocalDate valuationDate, BigDecimal costBasis) {
    }

    public record SavingsPlan(UUID id, UUID accountId, UUID debitAccountId, String name, String assetClass,
            BigDecimal amount, int dayOfMonth, LocalDate startDate, String recurrenceId) {
    }

    /** An event with a known outcome: the current-account balance expected at the end of {@code date}. */
    public record PlantedGroundTruth(String id, String description, LocalDate date, BigDecimal expectedBalance,
            BigDecimal premiumOverride) {
    }
}
