package de.financetool.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Minimum profile fields per recommendation topic (AD-23), bound from {@code config/profile-minimums.yaml}. A topic
 * is met when every group has at least one of its fields.
 */
@Validated
@ConfigurationProperties("profile-minimums")
public record ProfileMinimums(
        @NotNull @Valid DerivedFrom derivedFrom,
        @NotEmpty List<String> strictSuitabilityFields,
        @NotEmpty Map<String, List<List<String>>> topics) {
}
