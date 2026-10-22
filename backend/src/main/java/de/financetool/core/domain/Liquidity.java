package de.financetool.core.domain;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Core function {@code liquidAssets}: money available at short notice. Pure. */
public final class Liquidity {

    public static final String FUNCTION = "liquidAssets";
    public static final String OUTPUT = "liquidAssets";

    private Liquidity() {
    }

    /**
     * Current account plus overnight deposit balance.
     *
     * @param overnightBalance {@code null} without an overnight deposit
     */
    public static CoreComputation liquidAssets(SourcedNumber currentBalance, SourcedNumber overnightBalance) {
        Objects.requireNonNull(currentBalance, "currentBalance");
        BigDecimal overnight = overnightBalance == null ? BigDecimal.ZERO : overnightBalance.value();
        BigDecimal total = currentBalance.value().add(overnight).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("current", currentBalance.value());
        keyInputs.put("overnight", overnightBalance == null ? "none" : overnightBalance.value());

        List<FigureInput> inputs = new ArrayList<>();
        inputs.add(FigureInput.figure("Current account balance", currentBalance));
        inputs.add(overnightBalance == null
                ? FigureInput.text("Overnight deposit", "You don't have an overnight deposit.")
                : FigureInput.figure("Overnight deposit balance", overnightBalance));

        return new CoreComputation(FUNCTION, "liquid assets", "Based on your account balances.", keyInputs, inputs,
                List.of(new Figure(OUTPUT, "Liquid assets", total, Unit.EUR)),
                List.of("Liquid assets are your current account and overnight deposit balances; investments are not"
                        + " included."),
                Map.of());
    }
}
