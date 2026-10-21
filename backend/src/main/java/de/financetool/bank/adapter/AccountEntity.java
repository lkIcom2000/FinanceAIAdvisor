package de.financetool.bank.adapter;

import de.financetool.bank.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read model of {@code accounts}; rows are written by {@link BankRowWriter}. */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private String personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String iban;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    protected AccountEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getPersonaId() {
        return personaId;
    }

    public AccountType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getIban() {
        return iban;
    }

    public LocalDate getOpeningDate() {
        return openingDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }
}
