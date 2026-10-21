package de.financetool.shared.domain;

import java.io.Serializable;
import java.util.Objects;

/** The id of a Synthetic Persona: a slug {@code p-<name>}. */
public record PersonaId(String value) implements Serializable {

    public PersonaId {
        Objects.requireNonNull(value, "value");
        if (!value.matches("p-[a-z0-9-]+")) {
            throw new IllegalArgumentException("persona id must look like p-<name>: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
