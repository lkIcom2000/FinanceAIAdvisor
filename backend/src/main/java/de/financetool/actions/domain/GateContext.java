package de.financetool.actions.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What the Autonomy Gate decides on besides the Action, built by {@code ActionService} from one bank snapshot.
 *
 * @param liquidAssets current account plus overnight deposit
 * @param mandate the Mandate covering the Action, if any
 * @param sourceAfter the source account after the Action; {@code null} if it is neither the current account nor the
 *        overnight deposit (a covering Mandate's floor then counts as failed)
 */
public record GateContext(BigDecimal liquidAssets, boolean dataConfidenceHigh, boolean suitabilityComplete,
        Optional<MandateBounds> mandate, BigDecimal sourceAfter) {

    public GateContext {
        Objects.requireNonNull(liquidAssets, "liquidAssets");
        Objects.requireNonNull(mandate, "mandate");
    }
}
