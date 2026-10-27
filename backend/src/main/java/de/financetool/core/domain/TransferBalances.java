package de.financetool.core.domain;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.modulith.NamedInterface;

/**
 * The balances of a transfer's two accounts before and after it (function {@code accountBalance}), from one bank
 * snapshot; no evidence is stored for them.
 */
@NamedInterface("results")
public record TransferBalances(BigDecimal fromBefore, BigDecimal fromAfter, BigDecimal toBefore,
        BigDecimal toAfter) {

    public TransferBalances {
        Objects.requireNonNull(fromBefore, "fromBefore");
        Objects.requireNonNull(fromAfter, "fromAfter");
        Objects.requireNonNull(toBefore, "toBefore");
        Objects.requireNonNull(toAfter, "toAfter");
    }
}
