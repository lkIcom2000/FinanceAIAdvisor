package de.financetool.bank.adapter;

import de.financetool.bank.Coverage;
import de.financetool.bank.InsuredRole;
import de.financetool.bank.PaymentInterval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read model of {@code contracts}; rows are written by {@link BankRowWriter}. */
@Entity
@Table(name = "contracts")
public class ContractEntity {

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private String personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Coverage coverage;

    @Column(nullable = false)
    private String provider;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(nullable = false)
    private String policyholder;

    @Enumerated(EnumType.STRING)
    @Column(name = "insured_role", nullable = false)
    private InsuredRole insuredRole;

    private BigDecimal premium;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_interval")
    private PaymentInterval paymentInterval;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "debit_account_id")
    private UUID debitAccountId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    protected ContractEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getPersonaId() {
        return personaId;
    }

    public Coverage getCoverage() {
        return coverage;
    }

    public String getProvider() {
        return provider;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getPolicyholder() {
        return policyholder;
    }

    public InsuredRole getInsuredRole() {
        return insuredRole;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public PaymentInterval getPaymentInterval() {
        return paymentInterval;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public UUID getDebitAccountId() {
        return debitAccountId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }
}
