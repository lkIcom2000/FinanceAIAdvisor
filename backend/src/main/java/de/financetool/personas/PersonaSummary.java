package de.financetool.personas;

import io.swagger.v3.oas.annotations.media.Schema;

/** The persona a session acts for. */
public record PersonaSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {
}
