package de.financetool.shared.config;

/** What the phrasing model may see of persona data (FR-43). {@code raw}/{@code pseudonymised} only if E4 selects one. */
public enum DataExposureLevel {
    AGGREGATED,
    NONE
}
