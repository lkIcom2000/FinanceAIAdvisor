package de.financetool.advisory.domain;

import java.util.List;

/** The Check Chain's results on one text, one per {@link CheckId} in declaration order. */
public record ChainResult(List<CheckResult> results) {

    public static final ChainResult NONE = new ChainResult(List.of());

    public ChainResult {
        results = List.copyOf(results);
    }

    /** The applicable checks that did not pass ({@code failed} of {@code checks.py}). */
    public List<CheckResult> failed() {
        return results.stream().filter(CheckResult::failed).toList();
    }

    /** The checks that apply to the turn. */
    public List<CheckResult> applicable() {
        return results.stream().filter(CheckResult::applicable).toList();
    }

    public CheckResult get(CheckId id) {
        return results.stream().filter(r -> r.checkId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No result for " + id));
    }
}
