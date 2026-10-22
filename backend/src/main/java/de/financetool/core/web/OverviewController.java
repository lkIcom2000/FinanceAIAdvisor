package de.financetool.core.web;

import de.financetool.core.application.ShowHomeOverviewUseCase;
import de.financetool.shared.PersonaScope;
import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Financial Overview: every figure carries its figure key for "Why?". Persona from the session only. */
@Tag(name = "overview", description = "The Financial Overview")
@RestController
class OverviewController {

    @Schema(description = "A figure from Fine's calculation engine with the key of its provenance")
    record Figure(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[0-9a-f]{16}$") String provId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Decimal string",
                    pattern = "^-?\\d+(\\.\\d+)?$", example = "1974.00") String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Unit unit) {

        static Figure of(SourcedNumber number) {
            return new Figure(number.provId(), number.label(), number.value().toPlainString(), number.unit());
        }
    }

    record HomeOverview(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Figure currentAccount) {
    }

    private final ShowHomeOverviewUseCase showHome;

    OverviewController(ShowHomeOverviewUseCase showHome) {
        this.showHome = showHome;
    }

    @Operation(summary = "The Home tab's summary figures")
    @GetMapping("/api/overview/home")
    HomeOverview home(@Parameter(hidden = true) PersonaScope scope) {
        return new HomeOverview(Figure.of(showHome.show(scope).currentAccount()));
    }
}
