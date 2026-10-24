package de.financetool.knowledge;

import java.time.LocalDate;
import java.util.List;

/** Module API: the versioned Rule Parameters (minimal until Epic 4 extends it). */
public interface RuleParamPort {

    /**
     * The version of {@code key} valid at {@code asOf}.
     *
     * @throws IllegalStateException if the key is unknown or no version of it is valid at {@code asOf}
     */
    RuleParameter valid(String key, LocalDate asOf);

    /** Every Rule Parameter version valid at {@code asOf}, one per key ({@code all_valid}). */
    List<RuleParameter> allValid(LocalDate asOf);
}
