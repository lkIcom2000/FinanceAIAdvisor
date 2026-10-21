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

/** Read model of {@code holdings}; rows are written by {@link BankRowWriter}. */
@Entity
@Table(name = "holdings")
public class HoldingEntity {

    @Id
    private UUID id;

    @Column(name = "persona_id", nullable = false)
    private String personaId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false)
    private AssetClass assetClass;

    @Column(nullable = false)
    private BigDecimal units;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "valuation_date", nullable = false)
    private LocalDate valuationDate;

    @Column(name = "cost_basis", nullable = false)
    private BigDecimal costBasis;

    protected HoldingEntity() {
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

    public String getName() {
        return name;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public BigDecimal getUnits() {
        return units;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public LocalDate getValuationDate() {
        return valuationDate;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }
}
