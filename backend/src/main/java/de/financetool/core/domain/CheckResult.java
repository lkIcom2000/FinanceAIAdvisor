package de.financetool.core.domain;

import java.util.Objects;

/** One check on a Core figure, shown under "Checks"; {@code id} appears only as a small caption. */
public record CheckResult(String id, boolean passed, String description) {

    public CheckResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
    }
}
