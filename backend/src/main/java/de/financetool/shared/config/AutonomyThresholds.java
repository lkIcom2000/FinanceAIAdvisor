package de.financetool.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Autonomy Gate thresholds (FR-35), bound from {@code config/autonomy.yaml}. Fractions, not percent. */
@Validated
@ConfigurationProperties("autonomy")
public record AutonomyThresholds(
        @NotNull @Valid DerivedFrom derivedFrom,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal investmentShareOfLiquidAssets,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal dataConfidenceMinCoverage,
        @NotNull @Positive Integer dataConfidenceMaxGapDays) {
}
