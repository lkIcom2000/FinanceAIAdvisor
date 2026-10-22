/**
 * Trigger detection and Notifications.
 */
@ApplicationModule(allowedDependencies = { "core", "core :: results", "actions", "profile", "escalation", "audit", "personas", "documents", "shared" })
package de.financetool.monitoring;

import org.springframework.modulith.ApplicationModule;
