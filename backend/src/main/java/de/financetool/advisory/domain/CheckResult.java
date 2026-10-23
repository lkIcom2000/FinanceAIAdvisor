package de.financetool.advisory.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One check's result on one text ({@code CheckResult} of {@code experiments/finlab/checks.py}). A check that does not
 * apply to the turn says so explicitly with {@code applicable = false} (and {@code passed = true}).
 *
 * @param reasonCode why the check failed; empty when it passed
 * @param details what the check found, e.g. the named products (the experiment's keys)
 */
public record CheckResult(CheckId checkId, boolean applicable, boolean passed, String reasonCode,
        Map<String, Object> details) {

    public CheckResult {
        Objects.requireNonNull(checkId, "checkId");
        reasonCode = reasonCode == null ? "" : reasonCode;
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
    }

    /** A check that does not apply to this turn. */
    public static CheckResult notApplicable(CheckId checkId) {
        return new CheckResult(checkId, false, true, "", Map.of());
    }

    /** Applicable and not passed: what block routing acts on. */
    public boolean failed() {
        return applicable && !passed;
    }
}
