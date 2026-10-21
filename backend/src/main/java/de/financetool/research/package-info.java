/**
 * Persona reset, study preparation and audit export use cases (top of the module graph).
 */
@ApplicationModule(allowedDependencies = { "advisory", "monitoring", "actions", "escalation", "profile", "bank", "knowledge", "personas", "audit", "documents", "shared" })
package de.financetool.research;

import org.springframework.modulith.ApplicationModule;
