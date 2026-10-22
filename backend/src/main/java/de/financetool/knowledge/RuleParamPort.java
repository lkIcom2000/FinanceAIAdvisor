package de.financetool.knowledge;

import java.time.LocalDate;

/** Module API: the versioned Rule Parameters (minimal until Epic 4 extends it). */
public interface RuleParamPort {

    /**
     * The version of {@code key} valid at {@code asOf}.
     *
     * @throws IllegalStateException if the key is unknown or no version of it is valid at {@code asOf}
     */
    RuleParameter valid(String key, LocalDate asOf);
}
