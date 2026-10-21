package de.financetool.bank.adapter;

import de.financetool.bank.TransactionCategory;
import de.financetool.bank.TransactionOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read model of {@code transactions}; rows are written by {@link BankRowWriter}. */
@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private String personaId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionCategory category;

    @Column(nullable = false)
    private String counterparty;

    @Column(nullable = false)
    private String reference;

    @Column(name = "recurrence_id")
    private String recurrenceId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionOrigin origin;

    protected TransactionEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getPersonaId() {
        return personaId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public String getReference() {
        return reference;
    }

    public String getRecurrenceId() {
        return recurrenceId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public TransactionOrigin getOrigin() {
        return origin;
    }
}
