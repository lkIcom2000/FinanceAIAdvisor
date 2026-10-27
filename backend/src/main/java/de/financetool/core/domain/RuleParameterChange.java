package de.financetool.core.domain;

import de.financetool.shared.domain.Unit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Core function {@code ruleParameterChange}: a new Rule Parameter version with the value it replaces, as figures a
 * Notification can cite. Pure.
 */
public final class RuleParameterChange {

    public static final String FUNCTION = "ruleParameterChange";
    public static final String OUTPUT_VALUE = "value";
    public static final String OUTPUT_PREVIOUS = "previous";

    /** Plain names of the keys monitoring reports on; any other key is named by its words. */
    private static final Map<String, String> NAMES = Map.of(
            "ecb_deposit_facility_rate", "ECB deposit facility rate",
            "ecb_main_refinancing_rate", "ECB main refinancing rate",
            "ecb_marginal_lending_rate", "ECB marginal lending rate",
            "riester_basic_allowance", "Riester basic allowance",
            "riester_child_allowance_born_2008_or_later", "Riester child allowance (children born from 2008)",
            "riester_child_allowance_born_before_2008", "Riester child allowance (children born before 2008)",
            "riester_min_contribution_rate", "Riester minimum contribution rate",
            "riester_min_contribution_floor", "Riester minimum contribution",
            "riester_special_expense_cap", "Riester tax deduction cap");

    private RuleParameterChange() {
    }

    /** The rule in plain words, e.g. "ECB deposit facility rate". */
    public static String plainName(String key) {
        String name = NAMES.get(key);
        return name != null ? name : key.replace('_', ' ');
    }

    /**
     * @param previous the version it replaces, or {@code null} for the first version of the key
     */
    public static CoreComputation of(RuleParameterValue current, RuleParameterValue previous, Unit unit) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(unit, "unit");
        if (previous != null && !previous.key().equals(current.key())) {
            throw new IllegalArgumentException("a change compares two versions of one key");
        }
        String name = plainName(current.key());

        Map<String, Object> keyInputs = new LinkedHashMap<>();
        keyInputs.put("key", current.key());
        keyInputs.put("version", current.version());
        keyInputs.put("previousVersion", previous == null ? "none" : previous.version());

        List<Figure> outputs = new ArrayList<>();
        outputs.add(new Figure(OUTPUT_VALUE, name + " from " + Wording.date(current.validFrom()), current.value(),
                unit));
        if (previous != null) {
            outputs.add(new Figure(OUTPUT_PREVIOUS, name + " before " + Wording.date(current.validFrom()),
                    previous.value(), unit));
        }
        Map<String, String> versions = Map.of(current.key(), current.version());
        return new CoreComputation(FUNCTION, "rule change", "Based on Fine's dated rules.", keyInputs,
                List.of(FigureInput.text("Rule", name)), outputs,
                List.of(previous == null
                        ? "This is the first version of the " + name.toLowerCase(Locale.ROOT) + " Fine knows."
                        : "The new value applies from " + Wording.date(current.validFrom()) + "."),
                versions);
    }
}
