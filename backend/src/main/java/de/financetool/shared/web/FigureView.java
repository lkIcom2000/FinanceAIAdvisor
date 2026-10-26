package de.financetool.shared.web;

import de.financetool.shared.domain.SourcedNumber;
import de.financetool.shared.domain.Unit;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The API view of a Core figure, shared by the Overview and the chat so one figure key always arrives in one shape
 * (AD-19): the value as the backend's decimal string, never re-rounded by the client.
 */
@Schema(name = "Figure", description = "A figure from Fine's calculation engine with the key of its provenance")
public record FigureView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = SourcedNumber.PROV_ID_SCHEMA_PATTERN) String provId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Decimal string",
                pattern = FigureView.DECIMAL, example = "1974.00") String value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Unit unit) {

    /** A decimal string as the API sends figures. */
    public static final String DECIMAL = "^-?\\d+(\\.\\d+)?$";

    public static FigureView of(SourcedNumber number) {
        return new FigureView(number.provId(), number.label(), number.value().toPlainString(), number.unit());
    }
}
