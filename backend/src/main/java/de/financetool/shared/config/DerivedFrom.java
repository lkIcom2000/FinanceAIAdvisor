package de.financetool.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Where a configuration value came from (AD-25): the experiment id, its result file in the Colab output and the date.
 * Until the Colab results exist, {@code experiment} and {@code resultFile} are {@code none} and {@code note} says
 * "PRD starting value".
 */
public record DerivedFrom(@NotBlank String experiment, @NotBlank String resultFile, @NotNull LocalDate date,
        String note) {
}
