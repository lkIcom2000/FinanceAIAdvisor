package de.financetool.monitoring.domain;

/**
 * What a Notification tells the persona: {@code INFORMED} (L0), {@code RECOMMENDED} (L1), {@code ACTION} (a linked
 * Action whose state gives the status) or {@code ESCALATED} (a case for a human advisor).
 */
public enum NotificationOutcome {
    INFORMED, RECOMMENDED, ACTION, ESCALATED
}
