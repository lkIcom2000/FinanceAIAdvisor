package de.financetool.shared.config;

import java.util.Arrays;

/** The configured compliance layers (AD-3). Serialised as {@code A}, {@code A+B}, {@code A+B+C}. */
public enum ComplianceLevel {
    A("A"),
    A_B("A+B"),
    A_B_C("A+B+C");

    private final String value;

    ComplianceLevel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ComplianceLevel fromValue(String value) {
        return Arrays.stream(values())
                .filter(level -> level.value.equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "compliance must be one of A, A+B, A+B+C but was '" + value + "'"));
    }

    @Override
    public String toString() {
        return value;
    }
}
