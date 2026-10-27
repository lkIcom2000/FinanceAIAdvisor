package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What the Autonomy Gate decides on besides the Action, built by {@code ActionService} from one bank snapshot.
 *
 * @param liquidAssets current account plus overnight deposit
 * @param mandate the Mandate covering the Action, if any
 * @param emergencyFundAfter the emergency fund after the Action
 */
public record GateContext(BigDecimal liquidAssets, boolean dataConfidenceHigh, boolean suitabilityComplete,
        Optional<MandateBounds> mandate, BigDecimal emergencyFundAfter) {

    public GateContext {
        Objects.requireNonNull(liquidAssets, "liquidAssets");
        Objects.requireNonNull(mandate, "mandate");
        Objects.requireNonNull(emergencyFundAfter, "emergencyFundAfter");
    }
}
