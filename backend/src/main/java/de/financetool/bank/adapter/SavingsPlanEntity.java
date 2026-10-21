package de.financetool.bank.adapter;

import de.financetool.bank.AssetClass;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read model of {@code savings_plans}; rows are written by {@link BankRowWriter}. */
@Entity
@Table(name = "savings_plans")
public class SavingsPlanEntity {

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private String personaId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "debit_account_id", nullable = false)
    private UUID debitAccountId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false)
    private AssetClass assetClass;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "day_of_month", nullable = false)
    private short dayOfMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "recurrence_id", nullable = false)
    private String recurrenceId;

    protected SavingsPlanEntity() {
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

    public UUID getDebitAccountId() {
        return debitAccountId;
    }

    public String getName() {
        return name;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getRecurrenceId() {
        return recurrenceId;
    }
}
