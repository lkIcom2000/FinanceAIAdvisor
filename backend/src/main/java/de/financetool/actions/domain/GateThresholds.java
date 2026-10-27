package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The gate's thresholds, unpacked from {@code AutonomyThresholds} ({@code config/autonomy.yaml}) by the service: the
 * domain stays free of the configuration package (AD-1).
 *
 * @param investmentShareOfLiquidAssets the share of liquid assets above which an investment is capped at L2
 */
public record GateThresholds(BigDecimal investmentShareOfLiquidAssets) {

    public GateThresholds {
        Objects.requireNonNull(investmentShareOfLiquidAssets, "investmentShareOfLiquidAssets");
    }
}
