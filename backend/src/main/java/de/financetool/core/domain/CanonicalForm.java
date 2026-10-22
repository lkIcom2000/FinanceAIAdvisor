package de.financetool.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The canonical text of a Core function's inputs, hashed into the figure key (AD-2): map keys sorted, decimals as
 * plain strings (scale kept), dates ISO, lists in order, strings quoted. Equal inputs give equal text.
 */
public final class CanonicalForm {

    private CanonicalForm() {
    }

    public static String of(Map<String, ?> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        StringBuilder out = new StringBuilder();
        render(inputs, out);
        return out.toString();
    }

    private static void render(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case BigDecimal decimal -> out.append(decimal.toPlainString());
            case LocalDate date -> out.append(date);
            case String text -> quote(text, out);
            case Integer number -> out.append(number);
            case Long number -> out.append(number);
            case Boolean bool -> out.append(bool);
            case Enum<?> constant -> quote(constant.name(), out);
            case Map<?, ?> map -> {
                TreeMap<String, Object> sorted = new TreeMap<>();
                map.forEach((k, v) -> sorted.put((String) Objects.requireNonNull(k, "map key"), v));
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    quote(entry.getKey(), out);
                    out.append(':');
                    render(entry.getValue(), out);
                }
                out.append('}');
            }
            case List<?> list -> {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    render(list.get(i), out);
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException(
                    "no canonical form for " + value.getClass().getName() + "; use BigDecimal, not double");
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"').append(text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
    }
}
