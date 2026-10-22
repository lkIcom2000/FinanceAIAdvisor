package de.financetool.audit.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.financetool.audit.Provenance;
import de.financetool.audit.application.ShowProvenanceUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.Unit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a figure comes from (FR-8). The persona comes only from the session; another persona's figure key answers
 * exactly like an unknown one.
 */
@Tag(name = "provenance", description = "Where a figure comes from")
@RestController
class ProvenanceController {

    private static final Pattern PROV_ID = Pattern.compile("[0-9a-f]{16}");

    /** Problems name the collection, never the requested key. */
    private static final URI INSTANCE = URI.create("/api/provenance");

    record ProvenanceSource(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Provenance.SourceKind kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProvenanceInput(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(description = "Decimal string; absent for a text input", pattern = "^-?\\d+(\\.\\d+)?$")
            String value,
            @Schema(description = "Absent for a text input") Unit unit,
            @Schema(description = "Absent for a number input") String text,
            @Schema(description = "The input's own figure key, where it is itself a figure") String provId) {
    }

    record ProvenanceRuleParameter(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version) {
    }

    record ProvenanceCheck(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean passed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description) {
    }

    record ProvenanceResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[0-9a-f]{16}$") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProvenanceSource source,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Decimal string",
                    pattern = "^-?\\d+(\\.\\d+)?$") String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Unit unit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate asOf,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProvenanceInput> inputs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> assumptions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProvenanceRuleParameter> ruleParameters,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProvenanceCheck> checks) {
    }

    private final ShowProvenanceUseCase showProvenance;

    ProvenanceController(ShowProvenanceUseCase showProvenance) {
        this.showProvenance = showProvenance;
    }

    @Operation(summary = "Where a figure comes from: source, inputs, assumptions and checks")
    @ApiResponse(responseCode = "200", description = "The figure's provenance")
    @ApiResponse(responseCode = "400", description = "Not a figure key")
    @ApiResponse(responseCode = "404", description = "No such figure for this persona")
    @GetMapping("/api/provenance/{provId}")
    ProvenanceResponse show(@Parameter(hidden = true) PersonaScope scope,
            @PathVariable @Parameter(description = "16 lower-case hex characters") String provId) {
        if (!PROV_ID.matcher(provId).matches()) {
            throw problem(HttpStatus.BAD_REQUEST, "This is not a valid figure reference.");
        }
        return showProvenance.show(scope, provId).map(ProvenanceController::toResponse)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "We couldn't find this figure."));
    }

    private static ProvenanceResponse toResponse(Provenance p) {
        return new ProvenanceResponse(p.provId(),
                new ProvenanceSource(p.source().kind(), p.source().description()),
                p.label(), plain(p.value()), p.unit(), p.asOf(),
                p.inputs().stream().map(i -> new ProvenanceInput(i.label(), plain(i.value()), i.unit(), i.text(),
                        i.provId())).toList(),
                p.assumptions(),
                p.ruleParameters().stream().map(r -> new ProvenanceRuleParameter(r.id(), r.version())).toList(),
                p.checks().stream().map(c -> new ProvenanceCheck(c.id(), c.passed(), c.description())).toList());
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static ErrorResponseException problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(INSTANCE);
        return new ErrorResponseException(status, problem, null);
    }
}
