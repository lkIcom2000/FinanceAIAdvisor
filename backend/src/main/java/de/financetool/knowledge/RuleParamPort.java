package de.financetool.knowledge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Module API: the versioned Rule Parameters (minimal until Epic 4 extends it). */
public interface RuleParamPort {

    /**
     * The version of {@code key} valid at {@code asOf}.
     *
     * @throws NoValidRuleParameterException if the key is unknown or no version of it is valid at {@code asOf}
     */
    RuleParameter valid(String key, LocalDate asOf);

    /** Every Rule Parameter version valid at {@code asOf}, one per key ({@code all_valid}). */
    List<RuleParameter> allValid(LocalDate asOf);

    /** Every version whose validity starts after {@code after} and on or before {@code until}. */
    List<RuleParameter> startingBetween(LocalDate after, LocalDate until);

    /** The version of the same key that {@code version} follows: the latest one starting before it. */
    Optional<RuleParameter> previous(RuleParameter version);

    /** Every announced rule change, oldest announcement first. */
    List<RuleAnnouncement> announcements();
}
