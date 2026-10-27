package de.financetool.core.domain;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The Core measures the Autonomy Gate decides on, from one bank snapshot; no evidence is stored for them. The Data
 * Confidence result ({@link DataConfidence#of}) is carried as its plain parts, so the gateway exposes no check type.
 *
 * @param liquidAssets current account plus overnight deposit (function {@code liquidAssets})
 * @param dataConfidenceHigh whether Data Confidence is high
 * @param dataConfidenceCoverage the share of bookings with a category in the Data Confidence window
 * @param dataConfidenceLongestGapDays the longest gap without bookings in that window
 * @param overnightAfter the overnight deposit (the emergency fund) after the Action
 * @param emergencyFundTarget the emergency fund target (function {@code emergencyFundTarget}), or {@code null} if a
 *        Rule Parameter for it has no version valid at {@code asOf}
 */
@NamedInterface("results")
public record GateMeasures(BigDecimal liquidAssets, boolean dataConfidenceHigh, BigDecimal dataConfidenceCoverage,
        long dataConfidenceLongestGapDays, BigDecimal overnightAfter, BigDecimal emergencyFundTarget) {

    public GateMeasures {
        Objects.requireNonNull(liquidAssets, "liquidAssets");
        Objects.requireNonNull(dataConfidenceCoverage, "dataConfidenceCoverage");
        Objects.requireNonNull(overnightAfter, "overnightAfter");
    }
}
