/**
 * Turn orchestrator, classification, Check Chain, block routing, exposure filter and dialog state.
 */
@ApplicationModule(allowedDependencies = { "core", "core :: results", "profile", "knowledge", "llm", "audit", "escalation", "personas", "shared" })
package de.financetool.advisory;

import org.springframework.modulith.ApplicationModule;
