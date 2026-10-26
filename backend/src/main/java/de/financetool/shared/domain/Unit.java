package de.financetool.shared.domain;

/**
 * The unit of a {@link SourcedNumber}. {@code FRACTION} is a share (0.05 = 5 %), {@code PP} a difference in
 * percentage points, {@code FLAG} a yes (1) or no (0).
 */
public enum Unit {
    EUR, FRACTION, PP, MONTHS, YEARS, COUNT, FLAG
}
