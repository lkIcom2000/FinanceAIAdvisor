package de.financetool.monitoring.domain;

/** The kinds of Trigger a monitoring run detects (FR-30). Market price moves are never a Trigger. */
public enum TriggerType {
    FORECAST_SHORTFALL, EF_BELOW_TARGET, DUPLICATE_COVERAGE, RULE_CHANGE, RULE_ANNOUNCEMENT
}
